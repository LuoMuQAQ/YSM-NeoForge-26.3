// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ProjectileAnimatableCapabilityProvider {
    private ProjectileAnimatableCapabilityProvider() {}

    public static Optional<ProjectileAnimatableCapability> get(Entity entity) {
        return ClientEntityAttachments.projectile(entity, false);
    }

    public static Optional<ProjectileAnimatableCapability> initialize(Entity entity) {
        return ClientEntityAttachments.projectile(entity, true);
    }
}
