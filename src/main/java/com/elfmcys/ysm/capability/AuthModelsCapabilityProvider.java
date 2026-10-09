// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.YesSteveModel;
import java.util.Optional;
import net.minecraft.world.entity.Entity;

/** Domain access to the registered NeoForge entity attachment. */
public final class AuthModelsCapabilityProvider {
    private AuthModelsCapabilityProvider() {}

    public static Optional<AuthModelsCapability> get(Entity entity) {
        if (entity == null || !YesSteveModel.isAvailable() || !(entity instanceof net.minecraft.world.entity.player.Player)) {
            return Optional.empty();
        }
        return Optional.of(entity.getData(EntityAttachments.AUTH_MODELS));
    }
}
