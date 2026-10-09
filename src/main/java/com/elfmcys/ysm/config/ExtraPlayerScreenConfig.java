// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ExtraPlayerScreenConfig {
    public static ModConfigSpec.BooleanValue DISABLE_PLAYER_RENDER;
    public static ModConfigSpec.IntValue PLAYER_POS_X;
    public static ModConfigSpec.IntValue PLAYER_POS_Y;
    public static ModConfigSpec.DoubleValue PLAYER_SCALE;
    public static ModConfigSpec.DoubleValue PLAYER_YAW_OFFSET;

    public static void init(ModConfigSpec.Builder builder) {
        builder.push("extra_player_render");

        builder.comment("Whether to display player");
        DISABLE_PLAYER_RENDER = builder.define("DisablePlayerRender", false);

        builder.comment("Player position x in screen");
        PLAYER_POS_X = builder.defineInRange("PlayerPosX", 10, 0, Integer.MAX_VALUE);

        builder.comment("Player position y in screen");
        PLAYER_POS_Y = builder.defineInRange("PlayerPosY", 10, 0, Integer.MAX_VALUE);

        builder.comment("Player scale in screen");
        PLAYER_SCALE = builder.defineInRange("PlayerScale", 40, 8.0, 360.0);

        builder.comment("Player yaw offset in screen");
        PLAYER_YAW_OFFSET = builder.defineInRange("PlayerYawOffset", 5, Double.MIN_VALUE, Double.MAX_VALUE);

        builder.pop();
    }
}