package com.elfmcys.ysm.network.protocol;


import com.elfmcys.ysm.proto.network.AnimationState;
import com.elfmcys.ysm.proto.network.EffectStateSet;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.GameplayState;
import com.elfmcys.ysm.proto.network.ModelSelectionState;
import com.elfmcys.ysm.proto.network.PlayerStateReport;
import com.elfmcys.ysm.proto.network.PlayerStateUpdate;
import com.elfmcys.ysm.proto.network.RoamingState;
import com.elfmcys.ysm.proto.network.StateWriteMode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Structural and resource-limit validation performed before a player-state packet is enqueued. */
public final class PlayerStateValidator {
    private static final int MAX_TEXTURE_BYTES = 256;
    private static final int MAX_ANIMATION_BYTES = 256;
    private static final int MAX_EFFECTS = 64;
    private static final int MAX_EFFECT_ID_BYTES = 128;
    private static final int MAX_GAMEPLAY_VALUE = 1_000_000;

    private PlayerStateValidator() {
    }

    public static boolean validReport(PlayerStateReport report) {
        return validMode(report.mode())
                && validEntity(report.subject())
                && (!report.hasGameplay() || validGameplay(report.gameplayUnsafe()))
                && (!report.hasEffects() || validEffects(report.effectsUnsafe()))
                && (!report.hasAnimation() || validAnimation(report.animationUnsafe()))
                && (!report.hasRoaming() || validRoaming(report.roamingUnsafe()));
    }

    public static boolean validUpdate(PlayerStateUpdate update) {
        return validMode(update.mode())
                && validEntity(update.subject())
                && (update.mode() != StateWriteMode.STATE_WRITE_MODE_FULL || update.hasModel())
                && (!update.hasModel() || validModel(update.modelUnsafe()))
                && (!update.hasGameplay() || validGameplay(update.gameplayUnsafe()))
                && (!update.hasEffects() || validEffects(update.effectsUnsafe()))
                && (!update.hasAnimation() || validAnimation(update.animationUnsafe()))
                && (!update.hasRoaming() || validRoaming(update.roamingUnsafe()));
    }

    private static boolean validMode(StateWriteMode mode) {
        return mode == StateWriteMode.STATE_WRITE_MODE_FULL
                || mode == StateWriteMode.STATE_WRITE_MODE_DELTA;
    }

    private static boolean validEntity(EntityRef entity) {
        return entity != null && !entity.hasPlayerId()
                && entity.entityId() >= 0;
    }

    private static boolean validModel(ModelSelectionState model) {
        if (!ModelReferenceCodec.valid(model.model())) {
            return false;
        }
        return utf8Length(model.textureId()) <= MAX_TEXTURE_BYTES;
    }

    private static boolean validGameplay(GameplayState state) {
        if (state.hasExperienceLevel() && !between(state.experienceLevelUnsafe(), 0, MAX_GAMEPLAY_VALUE)
                || state.hasFoodLevel() && !between(state.foodLevelUnsafe(), 0, 20)
                || state.hasHealth() && !between(state.healthUnsafe(), 0, MAX_GAMEPLAY_VALUE)
                || state.hasMaxHealth() && !between(state.maxHealthUnsafe(), 0, MAX_GAMEPLAY_VALUE)
                || state.hasMoveXQ7() && !between(state.moveXQ7Unsafe(), -127, 127)
                || state.hasMoveYQ7() && !between(state.moveYQ7Unsafe(), -127, 127)
                || state.hasMoveZQ7() && !between(state.moveZQ7Unsafe(), -127, 127)) {
            return false;
        }
        return !state.hasHealth() || !state.hasMaxHealth()
                || state.healthUnsafe() <= state.maxHealthUnsafe();
    }

    private static boolean validEffects(EffectStateSet state) {
        if (state.effects().size() > MAX_EFFECTS) {
            return false;
        }
        var ids = new HashSet<String>();
        for (var effect : state.effects()) {
            if (effect.effectId().isBlank()
                    || utf8Length(effect.effectId()) > MAX_EFFECT_ID_BYTES
                    || effect.level() < 0 || effect.level() > 255
                    || !ids.add(effect.effectId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validAnimation(AnimationState state) {
        if (state.hasStopped()) {
            return state.stopped();
        }
        return state.hasAnimationId() && !state.animationId().isBlank()
                && utf8Length(state.animationId()) <= MAX_ANIMATION_BYTES;
    }

    private static boolean validRoaming(RoamingState state) {
        if (state.variables().size() > ProtocolLimits.MAX_ROAMING_VARIABLES) {
            return false;
        }
        var names = new HashSet<String>();
        for (var variable : state.variables()) {
            if (variable.name().isBlank()
                    || utf8Length(variable.name())
                    > ProtocolLimits.MAX_ROAMING_VARIABLE_NAME_BYTES
                    || !Float.isFinite(variable.value_())
                    || !names.add(variable.name())) {
                return false;
            }
        }
        return true;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }
}
