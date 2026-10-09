package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.format.parser.pojo.controller.AnimationController;
import com.elfmcys.ysm.format.parser.pojo.controller.AnimationControllerFile;
import com.elfmcys.ysm.format.parser.pojo.controller.State;
import com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationReference;
import com.elfmcys.ysm.proto.mixel.asset.model.data.BlendPoint;
import com.elfmcys.ysm.proto.mixel.asset.model.data.BlendTransition;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Transition;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class AnimationControllerBuilder {
    private AnimationControllerBuilder() {
    }

    public static com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationControllerFile build(AnimationControllerFile pojo) {
        var result = com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationControllerFile.newBuilder();
        var entries = new ArrayList<>(pojo.animationControllers.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (var entry : entries) {
            result.addControllers(toProto(entry.getKey(), entry.getValue()));
        }
        return result.build();
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationController toProto(
            String name, AnimationController src) {
        var dst = com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationController.newBuilder()
                .setName(name)
                .setDefaultState(Objects.requireNonNullElse(src.initialState, "default"));
        var states = new ArrayList<>(src.states.entrySet());
        states.sort(Map.Entry.comparingByKey());
        for (var state : states) {
            dst.addStates(toProto(state.getKey(), state.getValue()));
        }
        return dst.build();
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.State toProto(
            String name, State src) {
        var dst = com.elfmcys.ysm.proto.mixel.asset.model.data.State.newBuilder()
                .setName(name);
        for (var animation : src.animations) {
            var condition = Objects.requireNonNullElse(animation.condition, "");
            dst.addAnimations(AnimationReference.newBuilder()
                    .setName(Objects.requireNonNullElse(animation.name, ""))
                    .setCondition(SourcePrograms.of(condition.isBlank() ? "true" : condition))
                    .build());
        }
        for (var transition : src.transitions) {
            dst.addTransitions(Transition.newBuilder()
                    .setDst(transition.destStateName)
                    .setCondition(SourcePrograms.of(transition.condition))
                    .build());
        }
        if (!src.onEntry.isEmpty()) {
            dst.setOnEntry(SourcePrograms.statements(src.onEntry));
        }
        if (!src.onExit.isEmpty()) {
            dst.setOnExit(SourcePrograms.statements(src.onExit));
        }
        for (var sound : src.soundEffects) {
            dst.addSoundEffects(sound.effect);
        }
        if (src.blendTransition != null) {
            var blend = BlendTransition.newBuilder();
            if (src.blendTransition.linearLength != null) {
                blend.setLinearLength(src.blendTransition.linearLength);
            } else {
                for (var point : src.blendTransition.points.entrySet()) {
                    blend.addPoints(BlendPoint.newBuilder()
                            .setKey(point.getKey())
                            .setValue(point.getValue()).build());
                }
            }
            dst.setBlendTransition(blend.build());
        }
        return dst.build();
    }
}
