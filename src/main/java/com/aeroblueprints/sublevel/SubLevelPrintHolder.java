package com.aeroblueprints.sublevel;

import org.jetbrains.annotations.Nullable;

/**
 * Implemented by {@code SchematicPrinter} through a mixin so that the rest of the mod can
 * read and write the currently latched print session.
 */
public interface SubLevelPrintHolder {

    @Nullable
    SubLevelPrintSession aeroblueprints$getSession();

    void aeroblueprints$setSession(@Nullable SubLevelPrintSession session);
}
