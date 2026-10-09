// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.create;

import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.world.entity.player.Player;

public class CreateCompatInner {
    static boolean isHangingSkyhook(Player player) {
        return OptionalApi.query("create", false, () -> ((java.util.Set<?>) OptionalApi.getStatic(
                "com.simibubi.create.foundation.render.PlayerSkyhookRenderer", "hangingPlayers")).contains(player.getUUID()));
    }
}
