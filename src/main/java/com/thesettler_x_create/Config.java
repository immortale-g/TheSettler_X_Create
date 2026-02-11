package com.thesettler_x_create;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enable extra debug logging for TheSettler_x_Create")
            .define("debugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
