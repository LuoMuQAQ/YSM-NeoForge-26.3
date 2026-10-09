package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.client.animation.molang.CustomMolangParser;
import com.elfmcys.ysm.geckolib3.core.builder.Animation;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.builder.controller.AnimationControllerData;
import com.elfmcys.ysm.geckolib3.core.builder.controller.AnimationControllerState;
import com.elfmcys.ysm.geckolib3.core.controller.transition.IBlendTransition;
import com.elfmcys.ysm.geckolib3.core.controller.transition.LinearBlendTransition;
import com.elfmcys.ysm.geckolib3.core.controller.transition.SegmentedBlendTransition;
import com.elfmcys.ysm.geckolib3.core.keyframe.BoneAnimation;
import com.elfmcys.ysm.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.elfmcys.ysm.geckolib3.core.keyframe.bone.BoneKeyFrameProcessor;
import com.elfmcys.ysm.geckolib3.core.keyframe.bone.EasingType;
import com.elfmcys.ysm.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.elfmcys.ysm.geckolib3.core.keyframe.event.EventKeyFrame;
import com.elfmcys.ysm.geckolib3.core.molang.MolangParser;
import com.elfmcys.ysm.geckolib3.core.molang.value.IValue;
import com.elfmcys.ysm.geckolib3.file.AnimationControllerFile;
import com.elfmcys.ysm.proto.mixel.asset.model.data.BlendPoint;
import com.elfmcys.ysm.proto.mixel.asset.model.data.ExpressionValue;
import com.elfmcys.ysm.proto.mixel.asset.model.data.State;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public final class AnimationProtoMapper {
    private AnimationProtoMapper() {
    }

    public static Animation animation(com.elfmcys.ysm.proto.mixel.asset.model.data.Animation source) {
        var parser = CustomMolangParser.rentInstance();
        try {
            return animation(source, parser);
        } finally {
            CustomMolangParser.returnInstance(parser);
        }
    }

    public static AnimationControllerFile controllerFile(com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationControllerFile source) {
        var parser = CustomMolangParser.rentInstance();
        try {
            var controllers = new Object2ReferenceOpenHashMap<String, AnimationControllerData>();
            var sourceControllers = source.controllers();
            for (var controller : sourceControllers) {
                var states = new AnimationControllerState[
                        controller.states().size()];
                for (var index = 0; index < states.length; index++) {
                    states[index] = state(controller.states().get(index), parser);
                }
                controllers.put(controller.name(), new AnimationControllerData(controller.defaultState().orElse(""), states));
            }
            return new AnimationControllerFile(controllers);
        } finally {
            CustomMolangParser.returnInstance(parser);
        }
    }

    private static Animation animation(com.elfmcys.ysm.proto.mixel.asset.model.data.Animation source, MolangParser parser) {
        var sourceBones = source.boneAnimations();
        var bones = new ReferenceArrayList<BoneAnimation>(
                sourceBones.size());
        if (!sourceBones.isEmpty()) {
            for (var sourceBone : sourceBones) {
                bones.add(new BoneAnimation(sourceBone.boneName(),
                        frames(sourceBone.rotation(), parser, true),
                        frames(sourceBone.position(), parser, false),
                        frames(sourceBone.scale(), parser, false)));
            }
        }

        var sourceInstructions = source.instructionKeyframes();
        var instructions = new ReferenceArrayList<EventKeyFrame<IValue[]>>(
                sourceInstructions.size());
        if (!sourceInstructions.isEmpty()) {
            for (var sourceFrame : sourceInstructions) {
                var values = new IValue[]{
                        parser.parseExpression(sourceFrame.programs().source(), false)};
                instructions.add(new EventKeyFrame<>(sourceFrame.startTick(), values));
            }
        }
        instructions.sort(Comparator.comparingDouble(EventKeyFrame::getStartTick));

        var sourceSounds = source.soundKeyframes();
        var sounds = new ReferenceArrayList<EventKeyFrame<String>>(
                sourceSounds.size());
        if (!sourceSounds.isEmpty()) {
            for (var sourceFrame : sourceSounds) {
                sounds.add(new EventKeyFrame<>(sourceFrame.startTick(), sourceFrame.data()));
            }
        }

        var blendWeight = source.blendWeight().map(blend -> value(blend, parser)).orElse(null);
        return new Animation(source.name(), source.length(),
                loop(source.loop().orElse(com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType.LOOP_TYPE_UNSPECIFIED)),
                blendWeight, bones, sounds, instructions);
    }

    private static List<BoneKeyFrame> frames(
            Iterable<com.elfmcys.ysm.proto.mixel.asset.model.data.BoneKeyFrame> source,
            MolangParser parser, boolean rotation) {
        var raw = new ReferenceArrayList<RawBoneKeyFrame>();
        for (var frame : source) {
            var target = new RawBoneKeyFrame();
            target.startTick = frame.startTick();
            target.easingType =
                    frame.easingType() == com.elfmcys.ysm.proto.mixel.asset.model.data.EasingType.EASING_TYPE_CATMULLROM
                            ? EasingType.CATMULLROM : EasingType.LINEAR;
            if (!frame.pre().isEmpty()) {
                assign(frame.pre(), parser, target, false);
            }
            target.contiguous = frame.post().isEmpty();
            if (!target.contiguous) {
                assign(frame.post(), parser, target, true);
            }
            raw.add(target);
        }
        raw.sort(Comparator.comparingDouble(RawBoneKeyFrame::startTick));
        return BoneKeyFrameProcessor.process(raw, rotation);
    }

    private static void assign(
            ObjectList<ExpressionValue> source,
            MolangParser parser, RawBoneKeyFrame target, boolean post) {
        if (source.isEmpty()) {
            return;
        }
        var x = value(source.get(0), parser);
        var y = source.size() >= 3 ? value(source.get(1), parser) : x;
        var z = source.size() >= 3 ? value(source.get(2), parser) : x;
        if (post) {
            target.postXValue = x;
            target.postYValue = y;
            target.postZValue = z;
        } else {
            target.preXValue = x;
            target.preYValue = y;
            target.preZValue = z;
        }
    }

    private static IValue value(
            ExpressionValue source, MolangParser parser) {
        if (source.hasNum()) {
            return parser.getConstant(source.num());
        }
        if (source.hasProgram()) {
            return parser.parseExpression(source.program().source(), false);
        }
        return parser.getConstant(0);
    }

    @SuppressWarnings("unchecked")
    private static AnimationControllerState state(
            State source, MolangParser parser) {
        var animations = new Pair[source.animations().size()];
        for (var index = 0; index < animations.length; index++) {
            var animation = source.animations().get(index);
            var condition = animation.condition().source();
            animations[index] = Pair.of(animation.name(), StringUtils.isBlank(condition)
                    ? null : parser.parseExpression(condition, false));
        }
        var transitions = new Pair[source.transitions().size()];
        for (var index = 0; index < transitions.length; index++) {
            var transition = source.transitions().get(index);
            transitions[index] = Pair.of(transition.dst(),
                    parser.parseExpression(transition.condition().source(), false));
        }
        var onEntry = source.hasOnEntry()
                ? new IValue[]{parser.parseExpression(source.onEntry().orElseThrow().source(), false)}
                : new IValue[0];
        var onExit = source.hasOnExit()
                ? new IValue[]{parser.parseExpression(source.onExit().orElseThrow().source(), false)}
                : new IValue[0];
        return new AnimationControllerState(source.name(), animations, transitions, new String[0],
                onEntry, onExit, blend(source), false);
    }

    private static IBlendTransition blend(State source) {
        if (!source.hasBlendTransition()) {
            return new LinearBlendTransition(0);
        }
        var blend = source.blendTransition().orElseThrow();
        if (blend.hasLinearLength()) {
            return new LinearBlendTransition(blend.linearLengthUnsafe());
        }
        if (blend.points().size() < 2) {
            return new LinearBlendTransition(0);
        }
        var points = new ArrayList<BlendPoint>();
        blend.points().forEach(points::add);
        points.sort(Comparator.comparingDouble(BlendPoint::key));
        var times = new float[points.size()];
        var positions = new float[points.size()];
        for (var index = 0; index < points.size(); index++) {
            times[index] = points.get(index).key();
            positions[index] = points.get(index).value_();
        }
        return new SegmentedBlendTransition(times, positions);
    }

    private static LoopType loop(com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType loop) {
        return switch (loop) {
            case LOOP_TYPE_LOOP -> LoopType.LOOP;
            case LOOP_TYPE_HOLD_ON_LAST_FRAME -> LoopType.HOLD_ON_LAST_FRAME;
            default -> LoopType.PLAY_ONCE;
        };
    }
}
