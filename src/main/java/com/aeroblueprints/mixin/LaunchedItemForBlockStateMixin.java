package com.aeroblueprints.mixin;

import com.aeroblueprints.sublevel.PlotChunkHelper;
import com.aeroblueprints.sublevel.PlotTargetCarrier;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lands launched blocks inside the sub-level's plot instead of the world when the print
 * session is anchored to a physics structure.
 */
@Mixin(value = LaunchedItem.ForBlockState.class, remap = false)
public abstract class LaunchedItemForBlockStateMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true)
    private void aeroblueprints$placeIntoSubLevel(final Level world, final CallbackInfo ci) {
        final LaunchedItem.ForBlockState self = (LaunchedItem.ForBlockState) (Object) this;
        final BlockPos plotTarget = ((PlotTargetCarrier) self).aeroblueprints$getPlotTarget();
        if (plotTarget == null) {
            return;
        }

        // Block positions inside a plot are ordinary level positions: Sable intercepts the
        // chunk lookups and feeds block changes back into its physics mass tracker.
        PlotChunkHelper.ensurePlotChunk(world, plotTarget);
        BlockHelper.placeSchematicBlock(world, self.state, plotTarget, self.stack, self.data);
        ci.cancel();
    }
}
