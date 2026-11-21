package com.aeroblueprints.mixin;

import com.aeroblueprints.AeroBlueprintConfig;
import com.aeroblueprints.AeroBlueprints;
import com.aeroblueprints.sublevel.SubLevelPrintHolder;
import com.aeroblueprints.sublevel.SubLevelPrintSession;
import com.aeroblueprints.sublevel.SubLevelPrinting;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.foundation.blockEntity.IMergeableBE;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * The heart of the mod.
 * <p>
 * Create's {@code SchematicPrinter} works in absolute world coordinates: a schematic is
 * deployed at an anchor, and every block is placed at {@code anchor + offset}. Physics
 * structures do not live there - Sable stores their blocks in a plot, a reserved chunk
 * grid, and maps those coordinates into the world through a moving pose.
 * <p>
 * So when a print starts on top of a sub-level we remember where the anchor landed
 * <em>inside</em> the plot, and then translate every world target of the schematic into
 * plot space again. The block ends up part of the structure, moves with it, and updates
 * its mass and collisions like any block that was built by hand.
 */
@Mixin(value = SchematicPrinter.class, remap = false)
public abstract class SchematicPrinterMixin implements SubLevelPrintHolder {

    private static final String SESSION_KEY = "AeroBlueprintsSession";

    @Shadow
    private BlockPos schematicAnchor;

    @Shadow
    private BlockPos currentPos;

    @Shadow
    private boolean schematicLoaded;

    @Shadow
    private SchematicPrinter.PrintStage printStage;

    @Unique
    private @Nullable SubLevelPrintSession aeroblueprints$session;

    @Shadow
    public abstract boolean isErrored();

    @Shadow
    public abstract void handleCurrentTarget(SchematicPrinter.BlockTargetHandler blockHandler,
                                             SchematicPrinter.EntityTargetHandler entityHandler);

    @Override
    public @Nullable SubLevelPrintSession aeroblueprints$getSession() {
        return this.aeroblueprints$session;
    }

    @Override
    public void aeroblueprints$setSession(final @Nullable SubLevelPrintSession session) {
        this.aeroblueprints$session = session;
    }

    @Inject(method = "loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V",
            at = @At("HEAD"))
    private void aeroblueprints$clearBeforeLoad(final ItemStack blueprint, final Level world,
                                                final boolean processNBT, final CallbackInfo ci) {
        this.aeroblueprints$session = null;
    }

    @Inject(method = "loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V",
            at = @At("TAIL"))
    private void aeroblueprints$latchSession(final ItemStack blueprint, final Level world,
                                             final boolean processNBT, final CallbackInfo ci) {
        if (world.isClientSide() || !this.schematicLoaded || this.isErrored()) {
            return;
        }
        if (!(world instanceof final ServerLevel serverLevel) || this.schematicAnchor == null) {
            return;
        }

        this.aeroblueprints$session = SubLevelPrinting.latch(serverLevel, this.schematicAnchor);

        if (this.aeroblueprints$session != null && AeroBlueprintConfig.DEBUG.get()) {
            AeroBlueprints.LOGGER.info("[Aero Blueprints] print session latched: world {} -> plot {}",
                    this.aeroblueprints$session.worldAnchor(), this.aeroblueprints$session.plotAnchor());
        }
    }

    @Inject(method = "resetSchematic()V", at = @At("HEAD"))
    private void aeroblueprints$clearSession(final CallbackInfo ci) {
        this.aeroblueprints$session = null;
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void aeroblueprints$writeSession(final CompoundTag tag, final CallbackInfo ci) {
        if (this.aeroblueprints$session != null) {
            tag.put(SESSION_KEY, this.aeroblueprints$session.save());
        }
    }

    @Inject(method = "fromTag(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("TAIL"))
    private void aeroblueprints$readSession(final CompoundTag tag, final boolean clientPacket,
                                            final CallbackInfo ci) {
        this.aeroblueprints$session = tag.contains(SESSION_KEY)
                ? SubLevelPrintSession.load(tag.getCompound(SESSION_KEY))
                : null;
    }

    @Inject(method = "shouldPlaceBlock(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/schematics/SchematicPrinter$PlacementPredicate;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void aeroblueprints$shouldPlaceBlock(final Level world,
                                                 final SchematicPrinter.PlacementPredicate predicate,
                                                 final BlockPos pos,
                                                 final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelPrintSession session = this.aeroblueprints$session;
        if (session == null || this.printStage == SchematicPrinter.PrintStage.ENTITIES) {
            return;
        }
        if (!(world instanceof final ServerLevel serverLevel)) {
            return;
        }
        if (!SubLevelPrinting.isStillValid(serverLevel, session)) {
            this.aeroblueprints$session = null;
            return;
        }

        final BlockPos plotPos = session.toPlotTarget(pos);
        if (!this.aeroblueprints$isPlotTargetUsable(serverLevel, session, plotPos)) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(this.aeroblueprints$shouldPlaceBlockMapped(world, predicate, pos, plotPos));
    }

    /**
     * Accepts positions inside this structure's plot. Chunks that do not exist yet are fine -
     * they are created when the block actually lands (see {@code PlotChunkHelper}), which
     * keeps large prints from reserving the whole plot up front.
     */
    @Unique
    private boolean aeroblueprints$isPlotTargetUsable(final ServerLevel level,
                                                      final SubLevelPrintSession session,
                                                      final BlockPos plotPos) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        final ChunkPos chunkPos = new ChunkPos(plotPos);
        if (!container.inBounds(chunkPos)) {
            return false;
        }

        final LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) {
            return false;
        }

        final UUID sessionId = session.subLevelId();
        final UUID plotId = plot.getSubLevel().getUniqueId();
        return sessionId == null || plotId == null || sessionId.equals(plotId);
    }

    /**
     * Mirrors Create's {@code shouldPlaceBlock}, but reads the destination state from the
     * plot instead of the world, so replace modes, merging block entities and "already
     * placed" checks all see the structure's real blocks.
     */
    @Unique
    private boolean aeroblueprints$shouldPlaceBlockMapped(final Level world,
                                                          final SchematicPrinter.PlacementPredicate predicate,
                                                          final BlockPos worldPos,
                                                          final BlockPos plotPos) {
        final BlockState[] schematicState = new BlockState[1];
        final BlockEntity[] schematicBlockEntity = new BlockEntity[1];

        final BlockPos previousTarget = this.currentPos;
        this.currentPos = worldPos.subtract(this.schematicAnchor);
        try {
            this.handleCurrentTarget((target, blockState, blockEntity) -> {
                schematicState[0] = blockState;
                schematicBlockEntity[0] = blockEntity;
            }, (target, entity) -> {
            });
        } finally {
            this.currentPos = previousTarget;
        }

        final BlockState state = schematicState[0];
        if (state == null) {
            return false;
        }

        final BlockEntity blockEntity = schematicBlockEntity[0];
        final BlockState toReplace = world.getBlockState(plotPos);
        final BlockEntity toReplaceBlockEntity = world.getBlockEntity(plotPos);
        BlockState toReplaceOther = null;

        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            toReplaceOther = world.getBlockState(plotPos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            toReplaceOther = world.getBlockState(plotPos.above());
        }

        final boolean mergeBlockEntities = blockEntity != null
                && toReplaceBlockEntity instanceof IMergeableBE
                && toReplaceBlockEntity.getType().equals(blockEntity.getType());

        // No isLoaded() check here: plot chunks are created on demand when the block lands,
        // and every target outside this structure's plot was already rejected earlier.
        if (toReplace == state && !mergeBlockEntities) {
            return false;
        }
        if (toReplace.getDestroySpeed(world, plotPos) == -1
                || (toReplaceOther != null && toReplaceOther.getDestroySpeed(world, plotPos) == -1)) {
            return false;
        }

        final boolean isNormalCube = state.isRedstoneConductor(world, plotPos);
        return predicate.shouldPlace(worldPos, state, blockEntity, toReplace, toReplaceOther, isNormalCube);
    }
}
