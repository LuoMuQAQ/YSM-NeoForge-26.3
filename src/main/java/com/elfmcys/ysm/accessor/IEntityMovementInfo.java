package com.elfmcys.ysm.accessor;

import net.minecraft.util.Mth;

/** Cumulative horizontal movement, independent of the host's walk animation phase. */
public interface IEntityMovementInfo {
    WalkingDistance ysm$walkingDistance();

    record WalkingDistance(float previous, float current) {
        public float interpolated(float partialTick) {
            return Mth.lerp(partialTick, previous, current);
        }
    }
}
