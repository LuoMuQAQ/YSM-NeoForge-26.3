// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PlayerAnimatableCapabilityProvider {
    private PlayerAnimatableCapabilityProvider() {}

    public static Optional<PlayerAnimatableCapability> get(Entity entity) {
        return ClientEntityAttachments.player(entity);
    }

    public static Optional<PlayerAnimatableCapability> existing(Entity entity) {
        return ClientEntityAttachments.existingPlayer(entity);
    }
}
