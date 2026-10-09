// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.compat.backpack.sophisticated.SophisticatedCompat;
import com.elfmcys.ysm.client.renderer.CustomFirstPersonArmRenderer;
import com.elfmcys.ysm.client.renderer.CustomPlayerRenderer;
import com.elfmcys.ysm.client.renderer.CustomProjectileRenderer;
import com.elfmcys.ysm.client.renderer.CustomVehicleRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.Objects;

public class RegisterEntityRenderersEvent {
    private static CustomPlayerRenderer CUSTOM_PLAYER_RENDERER;
    private static CustomProjectileRenderer CUSTOM_PROJECTILE_RENDERER;
    private static CustomFirstPersonArmRenderer CUSTOM_FIRST_PERSON_RENDERER;
    private static CustomVehicleRenderer CUSTOM_VEHICLE_RENDERER;

    private static void init(EntityRendererProvider.Context context) {
        CUSTOM_PLAYER_RENDERER = new CustomPlayerRenderer(context);
        CUSTOM_PROJECTILE_RENDERER = new CustomProjectileRenderer();
        CUSTOM_FIRST_PERSON_RENDERER = new CustomFirstPersonArmRenderer();
        CUSTOM_VEHICLE_RENDERER = new CustomVehicleRenderer();
        SophisticatedCompat.addLayer();
    }

    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        // The host has baked this resource generation's models and exposes its
        // equipment renderer here. Rebuild our layers at the same reload boundary.
        init(event.getContext());
    }

    public static CustomPlayerRenderer getPlayerRenderer() {
        return Objects.requireNonNull(CUSTOM_PLAYER_RENDERER, "YSM player renderer is not initialized by AddLayers");
    }

    public static CustomProjectileRenderer getProjectRenderer() {
        return Objects.requireNonNull(CUSTOM_PROJECTILE_RENDERER, "YSM projectile renderer is not initialized by AddLayers");
    }

    public static CustomFirstPersonArmRenderer getFirstPersonArmRenderer() {
        return Objects.requireNonNull(CUSTOM_FIRST_PERSON_RENDERER, "YSM arm renderer is not initialized by AddLayers");
    }

    public static CustomVehicleRenderer getVehicleRenderer() {
        return Objects.requireNonNull(CUSTOM_VEHICLE_RENDERER, "YSM vehicle renderer is not initialized by AddLayers");
    }
}
