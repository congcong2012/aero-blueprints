package com.aeroblueprints.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A schematicannon print that is anchored to a Sable sub-level instead of the world.
 * <p>
 * {@code worldAnchor} is the position the blueprint was deployed at (the coordinates the
 * schematicannon works with), {@code plotAnchor} is where that same block lives inside the
 * sub-level's plot. Every other block of the schematic keeps the offset it has in
 * schematic space, which is exactly what {@link #toPlotTarget(BlockPos)} does.
 * <p>
 * Because plot coordinates are fixed while the sub-level silently moves and rotates,
 * latched sessions keep printing onto the same spot of the hull no matter how much the
 * physics structure drifts during the print.
 */
public record SubLevelPrintSession(BlockPos worldAnchor, BlockPos plotAnchor, @Nullable UUID subLevelId) {

    private static final String KEY_WORLD_ANCHOR = "WorldAnchor";
    private static final String KEY_PLOT_ANCHOR = "PlotAnchor";
    private static final String KEY_SUB_LEVEL = "SubLevel";

    /**
     * Maps a world-space schematic target into the sub-level's plot space.
     */
    public BlockPos toPlotTarget(final BlockPos worldTarget) {
        return this.plotAnchor.offset(
                worldTarget.getX() - this.worldAnchor.getX(),
                worldTarget.getY() - this.worldAnchor.getY(),
                worldTarget.getZ() - this.worldAnchor.getZ());
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_WORLD_ANCHOR, this.worldAnchor.asLong());
        tag.putLong(KEY_PLOT_ANCHOR, this.plotAnchor.asLong());
        if (this.subLevelId != null) {
            tag.putUUID(KEY_SUB_LEVEL, this.subLevelId);
        }
        return tag;
    }

    @Nullable
    public static SubLevelPrintSession load(final CompoundTag tag) {
        if (!tag.contains(KEY_WORLD_ANCHOR) || !tag.contains(KEY_PLOT_ANCHOR)) {
            return null;
        }

        final BlockPos worldAnchor = BlockPos.of(tag.getLong(KEY_WORLD_ANCHOR));
        final BlockPos plotAnchor = BlockPos.of(tag.getLong(KEY_PLOT_ANCHOR));
        final UUID id = tag.hasUUID(KEY_SUB_LEVEL) ? tag.getUUID(KEY_SUB_LEVEL) : null;
        return new SubLevelPrintSession(worldAnchor, plotAnchor, id);
    }
}
