// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.compat.backpack.sophisticated.SophisticatedCompat;
import com.elfmcys.ysm.client.renderer.CustomFirstPersonArmRenderer;
import com.elfmcys.ysm.client.renderer.CustomPlayerRenderer;
import com.elfmcys.ysm.client.renderer.CustomProjectileRenderer;
import com.elfmcys.ysm.client.renderer.CustomVehicleRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

public class RegisterEntityRenderersEvent {
    private static CustomPlayerRenderer CUSTOM_PLAYER_RENDERER;
    private static CustomProjectileRenderer CUSTOM_PROJECTILE_RENDERER;
    private static CustomFirstPersonArmRenderer CUSTOM_FIRST_PERSON_RENDERER;
    private static CustomVehicleRenderer CUSTOM_VEHICLE_RENDERER;

    private static void init(ResourceManager resourceManager) {
        CUSTOM_PLAYER_RENDERER = new CustomPlayerRenderer();
        CUSTOM_PROJECTILE_RENDERER = new CustomProjectileRenderer();
        CUSTOM_FIRST_PERSON_RENDERER = new CustomFirstPersonArmRenderer();
        CUSTOM_VEHICLE_RENDERER = new CustomVehicleRenderer();
        SophisticatedCompat.addLayer();
    }

    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        event.addListener(
                Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "renderer_init"),
                (ResourceManagerReloadListener) RegisterEntityRenderersEvent::init);
    }

    public static CustomPlayerRenderer getPlayerRenderer() {
        if (CUSTOM_PLAYER_RENDERER == null) {
            init(null);
        }
        return CUSTOM_PLAYER_RENDERER;
    }

    public static CustomProjectileRenderer getProjectRenderer() {
        if (CUSTOM_PROJECTILE_RENDERER == null) {
            init(null);
        }
        return CUSTOM_PROJECTILE_RENDERER;
    }

    public static CustomFirstPersonArmRenderer getFirstPersonArmRenderer() {
        if (CUSTOM_FIRST_PERSON_RENDERER == null) {
            init(null);
        }
        return CUSTOM_FIRST_PERSON_RENDERER;
    }

    public static CustomVehicleRenderer getVehicleRenderer() {
        if (CUSTOM_VEHICLE_RENDERER == null) {
            init(null);
        }
        return CUSTOM_VEHICLE_RENDERER;
    }
}
