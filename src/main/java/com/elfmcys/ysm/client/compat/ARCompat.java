// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import net.neoforged.fml.ModList;

public class ARCompat {
    private static final String MOD_ID = "acceleratedrendering";
    private static boolean INSTALLED;

    public static void init() {
        INSTALLED = ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }
}
