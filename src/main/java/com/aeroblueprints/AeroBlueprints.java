package com.aeroblueprints;

import com.aeroblueprints.sublevel.PlotTargetCarrier;
import com.aeroblueprints.sublevel.SubLevelPrintHolder;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Aero Blueprints - lets the Create schematicannon print schematics straight into
 * Sable sub-levels (Create: Aeronautics physics structures).
 */
@Mod(AeroBlueprints.MOD_ID)
public final class AeroBlueprints {

    public static final String MOD_ID = "aeroblueprints";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroBlueprints(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AeroBlueprintConfig.SPEC);

        if (Boolean.getBoolean("aeroblueprints.selftest")) {
            selfTest();
        }
    }

    /**
     * Forces the patched Create classes to load right now instead of on first use, so a
     * broken environment fails at startup with a readable message.
     */
    private static void selfTest() {
        final String[] targets = {
                "com.simibubi.create.content.schematics.SchematicPrinter",
                "com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity",
                "com.simibubi.create.content.schematics.cannon.LaunchedItem",
                "com.simibubi.create.content.schematics.cannon.LaunchedItem$ForBlockState",
                "com.simibubi.create.content.schematics.cannon.LaunchedItem$ForBelt"
        };

        for (final String target : targets) {
            try {
                final Class<?> clazz = Class.forName(target);
                final boolean holdsSession = SubLevelPrintHolder.class.isAssignableFrom(clazz);
                final boolean holdsPlotTarget = PlotTargetCarrier.class.isAssignableFrom(clazz);
                LOGGER.info("[Aero Blueprints] self-test: {} loaded (session={}, plotTarget={})",
                        target, holdsSession, holdsPlotTarget);
            } catch (final Throwable t) {
                LOGGER.error("[Aero Blueprints] self-test: failed to load {}", target, t);
            }
        }
    }
}
