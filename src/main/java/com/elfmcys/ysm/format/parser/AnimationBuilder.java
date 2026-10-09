package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.format.parser.pojo.animation.Animation;
import com.elfmcys.ysm.format.parser.pojo.animation.AnimationFile;
import com.elfmcys.ysm.format.parser.pojo.animation.BoneAnimation;
import com.elfmcys.ysm.format.parser.pojo.animation.keyframe.BoneKeyFrame;
import com.elfmcys.ysm.format.parser.pojo.animation.keyframe.BoneKeyFrameList;
import com.elfmcys.ysm.format.parser.pojo.animation.value.UnionValue;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.keyframe.bone.EasingType;
import com.elfmcys.ysm.proto.mixel.asset.model.data.EventKeyFrame;
import com.elfmcys.ysm.proto.mixel.asset.model.data.ExpressionValue;
import com.elfmcys.ysm.proto.mixel.asset.model.data.InstructionKeyFrame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

public class AnimationBuilder {
    private AnimationBuilder() {
    }

    public static com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationFile build(AnimationFile pojo) {
        var result = com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationFile.newBuilder();
        var animations = new ArrayList<>(pojo.animations);
        animations.sort(Comparator.comparing(value ->
                Objects.requireNonNullElse(value.animationName, "")));
        for (var animation : animations) {
            result.addAnimations(toProto(animation));
        }
        return result.build();
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.Animation toProto(Animation src) {
        var dst = com.elfmcys.ysm.proto.mixel.asset.model.data.Animation.newBuilder()
                .setName(Objects.requireNonNullElse(src.animationName, ""))
                .setLength(src.animationLength)
                .setLoop(toProto(src.loop));
        if (src.blendWeight != null) {
            dst.setBlendWeight(toProto(src.blendWeight));
        }
        var bones = new ArrayList<>(src.boneAnimations);
        bones.sort(Comparator.comparing(value ->
                Objects.requireNonNullElse(value.boneName, "")));
        for (var boneAnimation : bones) {
            dst.addBoneAnimations(toProto(boneAnimation));
        }
        for (var instruction : src.customInstructionKeyframes) {
            if (instruction.eventData.isEmpty()) {
                continue;
            }
            dst.addInstructionKeyframes(
                    InstructionKeyFrame.newBuilder()
                            .setPrograms(SourcePrograms.statements(instruction.eventData))
                            .setStartTick(instruction.startTick)
                            .build());
        }
        for (var sound : src.soundKeyFrames) {
            dst.addSoundKeyframes(EventKeyFrame.newBuilder()
                    .setData(Objects.requireNonNullElse(sound.eventData, ""))
                    .setStartTick(sound.startTick)
                    .build());
        }
        return dst.build();
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.BoneAnimation toProto(BoneAnimation src) {
        var dst = com.elfmcys.ysm.proto.mixel.asset.model.data.BoneAnimation.newBuilder()
                .setBoneName(Objects.requireNonNullElse(src.boneName, ""));
        addKeyFrames(dst::addRotation, src.rotationKeyFrames);
        addKeyFrames(dst::addPosition, src.positionKeyFrames);
        addKeyFrames(dst::addScale, src.scaleKeyFrames);
        return dst.build();
    }

    private static void addKeyFrames(
            Consumer<com.elfmcys.ysm.proto.mixel.asset.model.data.BoneKeyFrame> adder,
            BoneKeyFrameList list) {
        for (var keyFrame : list.keyFrames) {
            adder.accept(toProto(keyFrame));
        }
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.BoneKeyFrame toProto(BoneKeyFrame src) {
        var dst = com.elfmcys.ysm.proto.mixel.asset.model.data.BoneKeyFrame.newBuilder()
                .setStartTick(src.startTick)
                .setEasingType(src.easingType == EasingType.CATMULLROM
                        ? com.elfmcys.ysm.proto.mixel.asset.model.data.EasingType.EASING_TYPE_CATMULLROM
                        : com.elfmcys.ysm.proto.mixel.asset.model.data.EasingType.EASING_TYPE_LINEAR);
        if (src.preValue != null) {
            src.preValue.components.forEach(value -> dst.addPre(toProto(value)));
        }
        if (src.postValue != null) {
            src.postValue.components.forEach(value -> dst.addPost(toProto(value)));
        }
        return dst.build();
    }

    private static ExpressionValue toProto(UnionValue src) {
        var dst = ExpressionValue.newBuilder();
        if (src.type == UnionValue.ValueType.FLOAT) {
            dst.setNum(src.floatValue);
        } else if (src.type == UnionValue.ValueType.STRING) {
            dst.setProgram(SourcePrograms.of(src.stringValue));
        }
        return dst.build();
    }

    private static com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType toProto(LoopType loop) {
        if (loop == LoopType.LOOP) {
            return com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType.LOOP_TYPE_LOOP;
        }
        if (loop == LoopType.HOLD_ON_LAST_FRAME) {
            return com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType.LOOP_TYPE_HOLD_ON_LAST_FRAME;
        }
        return com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType.LOOP_TYPE_PLAY_ONCE;
    }
}
