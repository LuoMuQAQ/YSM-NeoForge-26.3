// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.realcamera;

import net.neoforged.fml.ModList;

public class RealCameraCompat {
    private static final String MOD_ID = "realcamera";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isActive() {
        if (INSTALLED) {
            return RealCameraCompatInner.isActive();
        }
        return false;
    }
}
