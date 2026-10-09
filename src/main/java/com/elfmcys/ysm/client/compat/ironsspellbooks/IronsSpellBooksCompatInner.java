// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.ironsspellbooks;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.animation.predicate.IAnimationPredicate;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

public class IronsSpellBooksCompatInner {
    private static final String PREFIX = "iss:";

    static void addInnerBinding(CtrlBinding binding) {
        binding.clientPlayerVar("iss_animation", ctx -> getAnimation(ctx.entity(), null));
    }

    static String getAnimation(LivingEntity entity, @Nullable AnimationEvent<CustomHumanoidEntity<?>> event) {
        return OptionalApi.query("irons_spellbooks", "", () -> {
            if (entity instanceof AbstractClientPlayer player) {
                var id = OptionalApi.getStatic("io.redspace.ironsspellbooks.api.spells.SpellAnimations", "ANIMATION_RESOURCE");
                var data = OptionalApi.callStatic("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess", "getPlayerAssociatedData", player);
                var layer = OptionalApi.call(data, "get", id);
                if (layer != null && OptionalApi.bool(OptionalApi.call(layer, "isActive"))) {
                    var keyframe = OptionalApi.call(layer, "getAnimation");
                    if (OptionalApi.instance("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer", keyframe)) {
                        if (event != null && OptionalApi.integer(OptionalApi.call(keyframe, "getTick")) == 0) {
                            event.getCodedController().indicateReload();
                        }
                        var extra = (java.util.Map<?, ?>) OptionalApi.get(OptionalApi.call(keyframe, "getData"), "extraData");
                        var name = extra.get("name");
                        return name instanceof String text ? text : "";
                    }
                }
            }
            return "";
        });
    }

    @Nullable
    static PlayState playAnimation(AnimationEvent<CustomHumanoidEntity<?>> event, LivingEntity entity) {
        String animation = getAnimation(entity, event);
        if (StringUtils.isBlank(animation)) {
            return null;
        }
        String animationName = PREFIX + animation;
        if (event.getAnimatableEntity().getAnimation(animationName) != null) {
            return IAnimationPredicate.playAnimation(event, animationName);
        }
        YesSteveModel.LOGGER.error(animationName);
        return null;
    }
}
