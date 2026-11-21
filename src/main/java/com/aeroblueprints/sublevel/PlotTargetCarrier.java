package com.aeroblueprints.sublevel;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by {@code LaunchedItem} through a mixin. A launched block normally remembers
 * only the world position it should land on; when it is aimed at a physics structure it
 * also remembers the equivalent position inside that structure's plot.
 */
public interface PlotTargetCarrier {

    @Nullable
    BlockPos aeroblueprints$getPlotTarget();

    void aeroblueprints$setPlotTarget(@Nullable BlockPos plotTarget);
}
