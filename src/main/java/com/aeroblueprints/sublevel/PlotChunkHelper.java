package com.aeroblueprints.sublevel;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Sable grows a plot by one chunk ring only after a block inside it changes, so the very
 * first block of a new area has nowhere to go yet. Create refuses to shoot at a position
 * whose chunk is not loaded, so the chunk is created right before the block lands.
 */
public final class PlotChunkHelper {

    private PlotChunkHelper() {
    }

    public static void ensurePlotChunk(final Level level, final BlockPos plotPos) {
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        final ChunkPos chunkPos = new ChunkPos(plotPos);
        if (!container.inBounds(chunkPos) || container.getChunk(chunkPos) != null) {
            return;
        }

        final LevelPlot plot = container.getPlot(chunkPos);
        if (plot != null) {
            plot.newEmptyChunk(chunkPos);
        }
    }
}
