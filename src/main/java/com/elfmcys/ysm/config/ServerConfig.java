// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.config;

import com.google.common.collect.Lists;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ServerConfig {
    public static ModConfigSpec.IntValue THREAD_COUNT;
    public static ModConfigSpec.IntValue BANDWIDTH_LIMIT;
    public static ModConfigSpec.IntValue DISPATCH_SOFT_LIMIT;
    public static ModConfigSpec.IntValue DISPATCH_HARD_LIMIT;
    public static ModConfigSpec.BooleanValue RESTRICTED_AUTH;
    public static ModConfigSpec.BooleanValue LOW_BANDWIDTH_USAGE;
    public static ModConfigSpec.BooleanValue CAN_SWITCH_MODEL;
    public static ModConfigSpec.ConfigValue<String> DEFAULT_MODEL_PATH;
    public static ModConfigSpec.ConfigValue<String> DEFAULT_MODEL_TEXTURE;

    // 禁止在玩家客户端 GUI 界面显示的模型相对路径
    public static ModConfigSpec.ConfigValue<List<String>> CLIENT_NOT_DISPLAY_MODEL_PATHS;

    public static ModConfigSpec init() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ServerConfig.init(builder);
        return builder.build();
    }

    private static void init(ModConfigSpec.Builder builder) {
        builder.comment("The relative path of the default model when a player first enters the game");
        DEFAULT_MODEL_PATH = builder.define("DefaultModelPath", "default");

        builder.comment("The default model texture when a player first enters the game");
        DEFAULT_MODEL_TEXTURE = builder.define("DefaultModelTexture", "default");

        builder.comment("Whether or not players are allowed to switch models");
        CAN_SWITCH_MODEL = builder.define("CanSwitchModel", true);

        builder.comment("Relative model paths that are not displayed on the client model selection screen");
        builder.comment("Example: [\"model.mxc\", \"pack/private/legacy.ysm\"]");
        CLIENT_NOT_DISPLAY_MODEL_PATHS = builder.define("ClientNotDisplayModelPaths", Lists.newArrayList());

        builder.push("server_scheduler");

        builder.comment("Concurrent level for processing models. Value 0 means AUTO.");
        THREAD_COUNT = builder.defineInRange("ThreadCount", 0, 0, Math.max(2, Runtime.getRuntime().availableProcessors() - 1));

        builder.comment("Global model distribution limit in Mbps. 0 means unlimited.");
        BANDWIDTH_LIMIT = builder.defineInRange("BandwidthLimit", 5, 0, 999);

        builder.comment("Per-player queue size that enables one-fragment round-robin visits.");
        DISPATCH_SOFT_LIMIT = builder.defineInRange("DispatchSoftLimit", 24, 1, 255);

        builder.comment("Atomic per-player logical-packet admission cap.");
        DISPATCH_HARD_LIMIT = builder.defineInRange("DispatchHardLimit", 48, 2, 256);

        builder.comment("Restrict unauthorized auth-model icon and chunk downloads.");
        RESTRICTED_AUTH = builder.define("RestrictedAuth", false);

        builder.comment("Suppress network synchronization of partial features to reduce bandwidth usage");
        builder.comment("Only effective when there are tons of players");
        LOW_BANDWIDTH_USAGE = builder.define("LowBandwidthUsage", false);

        builder.pop();
    }
}
