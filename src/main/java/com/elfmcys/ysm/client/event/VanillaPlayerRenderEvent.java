// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.event.api.SpecialPlayerRenderEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
@SuppressWarnings("removal")
public class VanillaPlayerRenderEvent {
    private static final Identifier STEVE_SKIN_LOCATION = Identifier.parse("textures/entity/player/wide/steve.png");
    private static final Identifier ALEX_SKIN_LOCATION = Identifier.parse("textures/entity/player/slim/alex.png");
    private static final String STEVE = "misc/2_steve";
    private static final String ALEX = "misc/1_alex";

    @SubscribeEvent
    public static void onRenderPlayer(SpecialPlayerRenderEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        Player player = event.getPlayer();
        if (isVanillaPlayer(event.getModelId()) && player instanceof AbstractClientPlayer clientPlayer) {
            Identifier location = getDefaultSkin(event.getModelId());
            Identifier loaded = clientPlayer.getSkin().body().texturePath();
            Identifier uuidDefault = DefaultPlayerSkin.get(clientPlayer.getUUID()).body().texturePath();
            if (!loaded.equals(uuidDefault)) {
                location = loaded;
            }
            event.setTextureLocationOverride(location);
        }
    }

    private static boolean isVanillaPlayer(String modelId) {
        return modelId.equals(STEVE) || modelId.equals(ALEX);
    }

    private static Identifier getDefaultSkin(String modelId) {
        return modelId.equals(STEVE) ? STEVE_SKIN_LOCATION : ALEX_SKIN_LOCATION;
    }
}
