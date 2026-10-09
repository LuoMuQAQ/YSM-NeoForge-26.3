// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.predicate;

import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.entity.IPreviewEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import static com.elfmcys.ysm.client.animation.predicate.IAnimationPredicate.playLoopAnimation;

public class MaidVehiclePredicate {
    @Nullable
    public static PlayState getMaidVehicleAnimation(AnimationEvent<CustomHumanoidEntity<?>> event, LivingEntity entity, Entity vehicle) {
        return OptionalApi.query(MaidApi.ID, null, () -> {
        if (event.getAnimatableEntity() instanceof IPreviewEntity) {
            return null;
        }
        if (MaidApi.is("entity.item.EntitySit", vehicle)) {
            String joyType = MaidApi.text(vehicle, "getJoyType");
            if (joyType.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "entity.favorability.Type", "GOMOKU"), "getTypeName"))) {
                return playLoopAnimation(event, "gomoku");
            } else if (joyType.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "entity.favorability.Type", "BOOKSHELF"), "getTypeName"))) {
                return playLoopAnimation(event, "bookshelf");
            } else if (joyType.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "entity.favorability.Type", "COMPUTER"), "getTypeName"))) {
                return playLoopAnimation(event, "computer");
            } else if (joyType.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "entity.favorability.Type", "KEYBOARD"), "getTypeName"))) {
                return playLoopAnimation(event, "keyboard");
            } else if (joyType.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "entity.favorability.Type", "ON_HOME_MEAL"), "getTypeName"))) {
                return playLoopAnimation(event, "picnic");
            }
        }
        if (MaidApi.is("entity.item.EntityChair", vehicle)) {
            return playLoopAnimation(event, "chair");
        }
        if (MaidApi.is("entity.item.EntityBroom", vehicle)) {
            return playLoopAnimation(event, "broom");
        }
        return null;
        });
    }
}
