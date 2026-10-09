// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client;

import com.elfmcys.ysm.client.entity.HumanoidStateTracker;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;

public class MaidStateTracker extends HumanoidStateTracker<LivingEntity> {
    public MaidStateTracker(LivingEntity entity) {
        super(entity);
    }

    @Override
    public void reset() {
        super.reset();
    }
}
