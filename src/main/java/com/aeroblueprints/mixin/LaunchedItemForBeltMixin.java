package com.aeroblueprints.mixin;

import com.aeroblueprints.sublevel.PlotChunkHelper;
import com.aeroblueprints.sublevel.PlotTargetCarrier;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Belts are placed as a whole chain by Create's own code, which reads {@code target} for
 * every segment. Swapping the field for the duration of the call lets the vanilla chain
 * logic run entirely in plot space.
 */
@Mixin(value = LaunchedItem.ForBelt.class, remap = false)
public abstract class LaunchedItemForBeltMixin {

    @Unique
    private @Nullable BlockPos aeroblueprints$worldTarget;

    @Inject(method = "place(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"))
    private void aeroblueprints$swapToPlotTarget(final Level world, final CallbackInfo ci) {
        final LaunchedItem self = (LaunchedItem) (Object) this;
        final BlockPos plotTarget = ((PlotTargetCarrier) self).aeroblueprints$getPlotTarget();
        if (plotTarget == null) {
            return;
        }

        this.aeroblueprints$worldTarget = self.target;
        self.target = plotTarget;
        PlotChunkHelper.ensurePlotChunk(world, plotTarget);
    }

    @Inject(method = "place(Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void aeroblueprints$restoreWorldTarget(final Level world, final CallbackInfo ci) {
        if (this.aeroblueprints$worldTarget != null) {
            ((LaunchedItem) (Object) this).target = this.aeroblueprints$worldTarget;
            this.aeroblueprints$worldTarget = null;
        }
    }
}
