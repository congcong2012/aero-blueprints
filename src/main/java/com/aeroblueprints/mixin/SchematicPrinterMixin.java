package com.aeroblueprints.mixin;

import com.aeroblueprints.AeroBlueprints;
import com.aeroblueprints.sublevel.SubLevelPrintHolder;
import com.aeroblueprints.sublevel.SubLevelPrintSession;
import com.aeroblueprints.sublevel.SubLevelPrinting;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.foundation.blockEntity.IMergeableBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
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

    /**
     * Set while a schematic is being loaded, when the blueprint's anchor had to be translated
     * out of plot space. Consumed by the tail of {@code loadSchematic}.
     */
    @Unique
    private @Nullable SubLevelPrintSession aeroblueprints$pendingSession;

    @Unique
    private @Nullable BlockPos aeroblueprints$originalAnchor;

    @Shadow
    public abstract boolean isErrored();

    @Shadow
    public abstract boolean isWorldEmpty();

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

    /**
     * When the blueprint was deployed on a physics structure, its anchor is a plot coordinate
     * (millions of blocks away). Left alone, the printer works entirely in plot space: every
     * target is checked with {@code world.isLoaded}, which is false for plot chunks that have
     * not been created yet, so those blocks are skipped without a trace.
     * <p>
     * Translating the anchor into world space for the loading step puts the print on the same
     * path as a normal blueprint, and the session maps each world target back into the plot
     * when the block actually lands.
     */
    @Inject(method = "loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V",
            at = @At("HEAD"))
    private void aeroblueprints$prepareAnchor(final ItemStack blueprint, final Level world,
                                              final boolean processNBT, final CallbackInfo ci) {
        this.aeroblueprints$session = null;
        this.aeroblueprints$pendingSession = null;

        if (world.isClientSide() || !(world instanceof final ServerLevel serverLevel)) {
            return;
        }
        if (!blueprint.has(AllDataComponents.SCHEMATIC_ANCHOR)) {
            return;
        }

        final BlockPos plotAnchor = blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR);
        if (plotAnchor == null) {
            return;
        }

        // Not inside a plot: the blueprint was deployed in the world, nothing to translate.
        final SubLevel subLevel = Sable.HELPER.getContaining(serverLevel, plotAnchor);
        if (subLevel == null) {
            return;
        }

        // Rotated or mirrored blueprints use the anchor as their rotation pivot, so moving the
        // anchor would skew the whole print. Leave those alone rather than producing garbage.
        final Rotation rotation = blueprint.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        final Mirror mirror = blueprint.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        if (rotation != Rotation.NONE || mirror != Mirror.NONE) {
            AeroBlueprints.LOGGER.warn(
                    "[Aero Blueprints] blueprint at {} is deployed on a sub-level but rotated/mirrored ({} / {}); "
                            + "printing it in plot space is not supported, deploy it without rotation",
                    plotAnchor, rotation, mirror);
            return;
        }

        final Vector3d worldAnchor = new Vector3d(plotAnchor.getX() + 0.5D, plotAnchor.getY() + 0.5D, plotAnchor.getZ() + 0.5D);
        subLevel.logicalPose().transformPosition(worldAnchor);
        final BlockPos translated = BlockPos.containing(worldAnchor.x, worldAnchor.y, worldAnchor.z);

        // Rewrite the component only for the duration of this call: Create reads the anchor
        // from the stack a few lines later, and the original value is restored on every
        // return path. The blueprint item itself is therefore left untouched.
        this.aeroblueprints$originalAnchor = plotAnchor;
        this.aeroblueprints$pendingSession = new SubLevelPrintSession(translated, plotAnchor, subLevel.getUniqueId());
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, translated);

        AeroBlueprints.LOGGER.info("[Aero Blueprints] blueprint anchor {} is plot space; loading it at world {}",
                plotAnchor, translated);
    }

    @Inject(method = "loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V",
            at = @At("RETURN"))
    private void aeroblueprints$restoreAnchor(final ItemStack blueprint, final Level world,
                                              final boolean processNBT, final CallbackInfo ci) {
        if (this.aeroblueprints$originalAnchor != null) {
            blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, this.aeroblueprints$originalAnchor);
            this.aeroblueprints$originalAnchor = null;
        }
    }

    @Inject(method = "loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V",
            at = @At("TAIL"))
    private void aeroblueprints$latchSession(final ItemStack blueprint, final Level world,
                                             final boolean processNBT, final CallbackInfo ci) {
        if (world.isClientSide() || !(world instanceof final ServerLevel serverLevel)) {
            this.aeroblueprints$pendingSession = null;
            return;
        }

        if (this.aeroblueprints$pendingSession != null) {
            this.aeroblueprints$session = this.aeroblueprints$pendingSession;
        } else if (this.schematicLoaded && !this.isErrored() && this.schematicAnchor != null) {
            this.aeroblueprints$session = SubLevelPrinting.latch(serverLevel, this.schematicAnchor);
        }

        this.aeroblueprints$pendingSession = null;

        AeroBlueprints.LOGGER.info(
                "[Aero Blueprints] schematic loaded: anchor={} errored={} empty={} session={}",
                this.schematicAnchor, this.isErrored(), this.isWorldEmpty(), this.aeroblueprints$session);
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
