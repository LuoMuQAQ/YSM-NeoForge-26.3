// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import dev.tr7zw.firstperson.api.FirstPersonAPI;
import dev.tr7zw.firstperson.api.PlayerOffsetHandler;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;

public class FirstPersonCompat {
    private static final String LEGACY_MOD_ID = "firstpersonmod";
    private static final String MOD_ID = "firstperson";
    private static volatile float HEAD_POS;
    private static boolean INSTALLED;

    public static void init() {
        INSTALLED = LoadingModList.get().getModFileById(MOD_ID) != null || LoadingModList.get().getModFileById(LEGACY_MOD_ID) != null;
        if (isInstalled()) {
            registerOffset();
            setHeadPos(24);
        }
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    private static void registerOffset() {
        FirstPersonAPI.registerPlayerHandler((PlayerOffsetHandler)(entity, delta, original, current) ->
                new Vec3(current.x(), 1.5f - HEAD_POS, current.z()));
    }

    public static boolean isRenderingPlayer() {
        return isInstalled() && FirstPersonAPI.isRenderingPlayer();
    }

    public static boolean shouldHideHead() {
        return isRenderingPlayer();
    }

    public static void setHeadPos(float headPos) {
        HEAD_POS = headPos / 16f;
    }

    public static boolean isEnabled() {
        return isInstalled() && FirstPersonAPI.isEnabled();
    }
}
