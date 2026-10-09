// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VehicleAnimatableCapabilityProvider {
    private VehicleAnimatableCapabilityProvider() {}

    public static Optional<VehicleAnimatableCapability> get(Entity entity) {
        return ClientEntityAttachments.vehicle(entity, false);
    }

    public static Optional<VehicleAnimatableCapability> initialize(Entity entity) {
        return ClientEntityAttachments.vehicle(entity, true);
    }
}
