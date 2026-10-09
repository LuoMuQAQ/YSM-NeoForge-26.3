// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.immersivemelodies;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.entity.HumanoidStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

public class ImmersiveMelodiesCompatInner {
    static void addInnerBinding(CtrlBinding binding) {
        binding.livingEntityVar("im_pitch", ctx -> {
            if (ctx.animatableEntity().getStateTracker() instanceof HumanoidStateTracker<?> tracker) {
                return tracker.getImmersiveMelodiesData().pitch;
            }
            return 0f;
        });

        binding.livingEntityVar("im_volume", ctx -> {
            if (ctx.animatableEntity().getStateTracker() instanceof HumanoidStateTracker<?> tracker) {
                return tracker.getImmersiveMelodiesData().volume;
            }
            return 0f;
        });

        binding.livingEntityVar("im_current", ctx -> {
            if (ctx.animatableEntity().getStateTracker() instanceof HumanoidStateTracker<?> tracker) {
                return tracker.getImmersiveMelodiesData().current;
            }
            return 0f;
        });

        binding.livingEntityVar("im_delta", ctx -> {
            if (ctx.animatableEntity().getStateTracker() instanceof HumanoidStateTracker<?> tracker) {
                return tracker.getImmersiveMelodiesData().delta;
            }
            return 0L;
        });

        binding.livingEntityVar("im_time", ctx -> {
            if (ctx.animatableEntity().getStateTracker() instanceof HumanoidStateTracker<?> tracker) {
                return tracker.getImmersiveMelodiesData().time;
            }
            return 0L;
        });
    }

    static void updateMelodyProgress(LivingEntity entity, ImmersiveMelodiesCompat.ImmersiveMelodiesData imData) {
        OptionalApi.query("immersive_melodies", null, () -> {
        Item item = (Item) OptionalApi.callStatic("immersive_melodies.client.animation.EntityModelAnimator", "getInstrument", entity);
        if (item != null) {
            float time = (Minecraft.getInstance().isPaused() ? 0.0F : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) + (float) entity.tickCount;
            var progress = OptionalApi.call(OptionalApi.getStatic("immersive_melodies.client.MelodyProgressManager", "INSTANCE"), "getProgress", entity);
            OptionalApi.call(progress, "visualTick", time);
            imData.pitch = OptionalApi.number(OptionalApi.call(progress, "getCurrentPitch"));
            imData.volume = OptionalApi.number(OptionalApi.call(progress, "getCurrentVolume"));
            imData.current = OptionalApi.number(OptionalApi.call(progress, "getCurrent"));
            imData.delta = OptionalApi.integer(OptionalApi.call(progress, "delta"));
            imData.time = OptionalApi.integer(OptionalApi.call(progress, "getTime"));
        }
        return null;
        });
    }
}
