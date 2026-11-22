package com.aeroblueprints;

import com.aeroblueprints.sublevel.PlotChunkHelper;
import com.aeroblueprints.sublevel.SubLevelPrintHolder;
import com.aeroblueprints.sublevel.SubLevelPrintSession;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreatePaths;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic only, enabled with {@code -Daeroblueprints.selftest=true}.
 * <p>
 * Assembles a real Sable sub-level, then loads a blueprint whose anchor sits inside that
 * sub-level's plot - exactly the situation that misbehaved in game - and reports what the
 * schematic printer ended up with.
 */
@EventBusSubscriber(modid = AeroBlueprints.MOD_ID)
public final class SelfTest {

    private static final BlockPos BASE = new BlockPos(120, 80, 120);

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        if (!Boolean.getBoolean("aeroblueprints.selftest")) {
            return;
        }

        try {
            endToEnd(event.getServer());
        } catch (final Throwable t) {
            AeroBlueprints.LOGGER.error("[diag] end-to-end check threw", t);
        }
    }

    private static void endToEnd(final MinecraftServer server) throws Exception {
        final ServerLevel level = server.overworld();

        // 1. a 2x2x2 stone cube in the world
        final List<BlockPos> cube = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    cube.add(BASE.offset(x, y, z));
                }
            }
        }
        cube.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));

        // 2. turn it into a sub-level; BASE becomes the centre block of the plot
        final BoundingBox3i bounds = new BoundingBox3i(
                BASE.getX(), BASE.getY(), BASE.getZ(),
                BASE.getX() + 1, BASE.getY() + 1, BASE.getZ() + 1);
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, BASE, cube, bounds);
        if (subLevel == null) {
            AeroBlueprints.LOGGER.error("[diag] failed to assemble a sub-level");
            return;
        }
        final BlockPos plotAnchor = subLevel.getPlot().getCenterBlock();
        AeroBlueprints.LOGGER.info("[diag] assembled sub-level {} at {} (plot anchor {})",
                subLevel.getUniqueId(), BASE, plotAnchor);

        // 3. save a blueprint file that contains a 2x2x2 cube
        final Path dir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve("selftest");
        Files.createDirectories(dir);
        final StructureTemplate template = template(level);
        final CompoundTag saved = template.save(new CompoundTag());
        try (OutputStream out = Files.newOutputStream(dir.resolve("aeroselftest.nbt"))) {
            NbtIo.writeCompressed(saved, out);
        }

        // 4. a blueprint deployed at the plot anchor, as if aimed at the hull
        final ItemStack blueprint = AllItems.SCHEMATIC.asStack();
        blueprint.set(AllDataComponents.SCHEMATIC_OWNER, "selftest");
        blueprint.set(AllDataComponents.SCHEMATIC_FILE, "aeroselftest.nbt");
        blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, plotAnchor);
        blueprint.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        blueprint.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        blueprint.set(AllDataComponents.SCHEMATIC_BOUNDS, new Vec3i(2, 2, 2));

        // 5. load it the way the cannon does
        final SchematicPrinter printer = new SchematicPrinter();
        printer.loadSchematic(blueprint, level, true);

        final SubLevelPrintSession session = ((SubLevelPrintHolder) printer).aeroblueprints$getSession();
        AeroBlueprints.LOGGER.info(
                "[diag] blueprint load: loaded={} errored={} empty={} anchor={} session={}",
                printer.isLoaded(), printer.isErrored(), printer.isWorldEmpty(), printer.getAnchor(), session);

        // 6. the blueprint item itself must be untouched
        AeroBlueprints.LOGGER.info("[diag] blueprint anchor after load: {} (expected {})",
                blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR), plotAnchor);

        // 7. fire one shot at the structure and check it actually lands inside the plot
        if (session != null) {
            printer.advanceCurrentPos();
            final BlockPos worldTarget = printer.getCurrentTarget();
            final boolean canPlace = worldTarget != null
                    && printer.shouldPlaceBlock(level, (pos, state, be, toReplace, other, normalCube) -> true, worldTarget);
            final BlockPos plotTarget = session.toPlotTarget(worldTarget == null ? plotAnchor : worldTarget);
            PlotChunkHelper.ensurePlotChunk(level, plotTarget);
            BlockHelper.placeSchematicBlock(level, Blocks.GOLD_BLOCK.defaultBlockState(), plotTarget,
                    ItemStack.EMPTY, null);
            final BlockState landed = level.getBlockState(plotTarget);
            AeroBlueprints.LOGGER.info("[diag] shot: worldTarget={} canPlace={} plotTarget={} landed={}",
                    worldTarget, canPlace, plotTarget, landed);
        }
    }

    private static ListTag ints(final int... values) {
        final ListTag tag = new ListTag();
        for (final int value : values) {
            tag.add(IntTag.valueOf(value));
        }
        return tag;
    }

    private static StructureTemplate template(final ServerLevel level) {
        final CompoundTag root = new CompoundTag();
        root.put("size", ints(2, 2, 2));

        final ListTag palette = new ListTag();
        final CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        final ListTag list = new ListTag();
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    final CompoundTag block = new CompoundTag();
                    block.put("pos", ints(x, y, z));
                    block.putInt("state", 0);
                    list.add(block);
                }
            }
        }
        root.put("blocks", list);

        final StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), root);
        return template;
    }

    private SelfTest() {
    }
}
