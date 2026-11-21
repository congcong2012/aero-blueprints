package com.aeroblueprints;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config. Values are read lazily by the mixins, so they always reflect the
 * current file rather than a snapshot taken at class-load time.
 */
public final class AeroBlueprintConfig {

    public static final ModConfigSpec SPEC;

    /** Master switch: when off, the cannon behaves exactly like vanilla Create. */
    public static final ModConfigSpec.BooleanValue ENABLED;

    /** Prints extra information about latched print sessions to the log. */
    public static final ModConfigSpec.BooleanValue DEBUG;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        ENABLED = builder
                .comment("Allow the schematicannon to print schematics into Sable sub-levels (physics structures).")
                .define("enableSubLevelPrinting", true);

        DEBUG = builder
                .comment("Log which sub-level a print session was latched onto.")
                .define("debugLogging", false);

        SPEC = builder.build();
    }

    private AeroBlueprintConfig() {
    }
}
