package com.aeroblueprints.mixin;

import com.aeroblueprints.sublevel.PlotTargetCarrier;
import com.aeroblueprints.sublevel.SubLevelPrintHolder;
import com.aeroblueprints.sublevel.SubLevelPrintSession;
import com.aeroblueprints.sublevel.SubLevelPrinting;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Tags every shot of the cannon with the plot-space position it should land on. The
 * world-space target stays as-is so the shot's travel time and animation are unchanged.
 */
@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class SchematicannonBlockEntityMixin {

    @Shadow
    public SchematicPrinter printer;

    @Shadow
    public List<LaunchedItem> flyingBlocks;

    /**
     * Create checks the blueprint anchor against the cannon's position with a 256 block
     * limit. Structures live in plot space, millions of blocks away, so the two positions
     * must be brought into the same coordinate space first.
     */
    @Redirect(method = "initializePrinter(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;getAnchor()Lnet/minecraft/core/BlockPos;"))
    private BlockPos aeroblueprints$anchorInCannonSpace(final SchematicPrinter printer) {
        final BlockPos anchor = printer.getAnchor();
        if (anchor == null) {
            return null;
        }

        final SchematicannonBlockEntity self = (SchematicannonBlockEntity) (Object) this;
        if (self.getLevel() instanceof final ServerLevel serverLevel) {
            return SubLevelPrinting.toReferenceSpace(serverLevel, anchor, self.getBlockPos());
        }

        return anchor;
    }

    @Inject(method = "launchBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"))
    private void aeroblueprints$tagLaunchedBlock(final BlockPos target, final ItemStack stack,
                                                 final BlockState state, final CompoundTag data,
                                                 final CallbackInfo ci) {
        this.aeroblueprints$tagLastShot(target);
    }

    @Inject(method = "launchBelt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I[Lcom/simibubi/create/content/kinetics/belt/BeltBlockEntity$CasingType;)V",
            at = @At("TAIL"))
    private void aeroblueprints$tagLaunchedBelt(final BlockPos target, final BlockState state,
                                                final int length, final BeltBlockEntity.CasingType[] casings,
                                                final CallbackInfo ci) {
        this.aeroblueprints$tagLastShot(target);
    }

    @Unique
    private void aeroblueprints$tagLastShot(final BlockPos worldTarget) {
        if (this.flyingBlocks.isEmpty() || !(this.printer instanceof final SubLevelPrintHolder holder)) {
            return;
        }

        final SubLevelPrintSession session = holder.aeroblueprints$getSession();
        if (session == null) {
            return;
        }

        final LaunchedItem shot = this.flyingBlocks.get(this.flyingBlocks.size() - 1);
        if (shot instanceof final PlotTargetCarrier carrier) {
            carrier.aeroblueprints$setPlotTarget(session.toPlotTarget(worldTarget));
        }
    }
}
