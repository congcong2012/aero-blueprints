package com.aeroblueprints.mixin;

import com.aeroblueprints.sublevel.PlotTargetCarrier;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries the plot-space landing position of a launched schematic block. The world-space
 * target is kept untouched, so the flying block animation and travel time keep working.
 */
@Mixin(value = LaunchedItem.class, remap = false)
public abstract class LaunchedItemMixin implements PlotTargetCarrier {

    private static final String PLOT_TARGET_KEY = "AeroBlueprintsPlotTarget";

    @Unique
    private @Nullable BlockPos aeroblueprints$plotTarget;

    @Override
    public @Nullable BlockPos aeroblueprints$getPlotTarget() {
        return this.aeroblueprints$plotTarget;
    }

    @Override
    public void aeroblueprints$setPlotTarget(final @Nullable BlockPos plotTarget) {
        this.aeroblueprints$plotTarget = plotTarget;
    }

    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"))
    private void aeroblueprints$writePlotTarget(final HolderLookup.Provider registries,
                                                final CallbackInfoReturnable<CompoundTag> cir) {
        if (this.aeroblueprints$plotTarget != null) {
            cir.getReturnValue().putLong(PLOT_TARGET_KEY, this.aeroblueprints$plotTarget.asLong());
        }
    }

    @Inject(method = "readNBT(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/core/HolderGetter;)V",
            at = @At("TAIL"))
    private void aeroblueprints$readPlotTarget(final CompoundTag tag,
                                               final HolderLookup.Provider registries,
                                               final HolderGetter<Block> holderGetter,
                                               final CallbackInfo ci) {
        if (tag.contains(PLOT_TARGET_KEY)) {
            this.aeroblueprints$plotTarget = BlockPos.of(tag.getLong(PLOT_TARGET_KEY));
        }
    }
}
