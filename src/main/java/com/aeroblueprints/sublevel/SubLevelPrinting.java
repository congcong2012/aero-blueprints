package com.aeroblueprints.sublevel;

import com.aeroblueprints.AeroBlueprintConfig;
import com.aeroblueprints.AeroBlueprints;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Bridges Create's world-space schematic printing to Sable's plot space.
 */
public final class SubLevelPrinting {

    /**
     * Slack around the anchor, so that aiming at the hull's edge - or at the empty corner
     * of a schematic's bounding box - still finds the structure.
     */
    private static final double ANCHOR_SLACK = 1.5D;

    private SubLevelPrinting() {
    }

    /**
     * Tries to latch a print session for the given blueprint anchor.
     *
     * @return the session, or {@code null} when the anchor is not on a sub-level and the
     * cannon should keep using vanilla behaviour
     */
    @Nullable
    public static SubLevelPrintSession latch(final ServerLevel level, final BlockPos worldAnchor) {
        if (!AeroBlueprintConfig.ENABLED.get()) {
            return null;
        }

        // Already plot space: the blueprint was deployed while standing on a sub-level, so
        // Create resolves targets in plot coordinates on its own. Nothing to redirect.
        if (Sable.HELPER.getContaining(level, worldAnchor) != null) {
            return null;
        }

        final ServerSubLevel subLevel = findSubLevel(level, worldAnchor);
        if (subLevel == null) {
            return null;
        }

        final Vector3d local = new Vector3d(worldAnchor.getX() + 0.5D, worldAnchor.getY() + 0.5D, worldAnchor.getZ() + 0.5D);
        subLevel.logicalPose().transformPositionInverse(local);
        final BlockPos plotAnchor = BlockPos.containing(local.x, local.y, local.z);

        if (AeroBlueprintConfig.DEBUG.get()) {
            AeroBlueprints.LOGGER.info("[Aero Blueprints] anchoring print at {} to sub-level {} (plot anchor {})",
                    worldAnchor, subLevel.getUniqueId(), plotAnchor);
        }

        return new SubLevelPrintSession(worldAnchor, plotAnchor, subLevel.getUniqueId());
    }

    /**
     * @return the sub-level whose world-space bounds contain the given block, or {@code null}
     */
    @Nullable
    private static ServerSubLevel findSubLevel(final ServerLevel level, final BlockPos pos) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        final BoundingBox3d query = new BoundingBox3d(pos).expand(ANCHOR_SLACK);
        final Vector3d point = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

        ServerSubLevel best = null;
        double bestDistance = Double.MAX_VALUE;
        ServerSubLevel bestNear = null;
        double bestNearDistance = Double.MAX_VALUE;

        for (final SubLevel candidate : container.getAllSubLevels()) {
            if (!(candidate instanceof final ServerSubLevel serverSubLevel) || candidate.isRemoved()) {
                continue;
            }

            final BoundingBox3dc bounds = candidate.boundingBox();
            if (!bounds.intersects(query)) {
                continue;
            }

            final double distance = bounds.center(new Vector3d()).distanceSquared(point);
            if (bounds.contains(point) && distance < bestDistance) {
                bestDistance = distance;
                best = serverSubLevel;
            }
            if (distance < bestNearDistance) {
                bestNearDistance = distance;
                bestNear = serverSubLevel;
            }
        }

        return best != null ? best : bestNear;
    }

    /**
     * Sessions are restored from NBT after a reload, so make sure the structure they point
     * at still exists before writing blocks into its plot.
     */
    public static boolean isStillValid(final ServerLevel level, final SubLevelPrintSession session) {
        final UUID id = session.subLevelId();
        if (id == null) {
            return true;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        final SubLevel subLevel = container.getSubLevel(id);
        return subLevel != null && !subLevel.isRemoved();
    }

    /**
     * Create refuses to start a print when the blueprint anchor is more than 256 blocks away
     * from the cannon. Plot coordinates are millions of blocks away from the world, so that
     * check has to be done in a single coordinate space: express the anchor in whatever space
     * the cannon happens to be in.
     */
    public static BlockPos toReferenceSpace(final ServerLevel level, final BlockPos anchor, final BlockPos referencePos) {
        final SubLevel referenceSubLevel = Sable.HELPER.getContaining(level, referencePos);
        final SubLevel anchorSubLevel = Sable.HELPER.getContaining(level, anchor);

        if (referenceSubLevel == null && anchorSubLevel != null) {
            // Blueprint deployed on a structure, cannon standing in the world.
            final Vector3d world = centerOf(anchor);
            anchorSubLevel.logicalPose().transformPosition(world);
            return BlockPos.containing(world.x, world.y, world.z);
        }

        if (referenceSubLevel != null && anchorSubLevel == null) {
            // Cannon mounted on a structure, blueprint deployed in the world.
            final Vector3d local = centerOf(anchor);
            referenceSubLevel.logicalPose().transformPositionInverse(local);
            return BlockPos.containing(local.x, local.y, local.z);
        }

        return anchor;
    }

    private static Vector3d centerOf(final BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
}
