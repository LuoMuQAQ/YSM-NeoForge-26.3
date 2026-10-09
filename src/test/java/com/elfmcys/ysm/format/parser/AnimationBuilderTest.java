package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.format.parser.pojo.animation.Animation;
import com.elfmcys.ysm.format.parser.pojo.animation.AnimationFile;
import com.elfmcys.ysm.format.parser.pojo.animation.keyframe.EventKeyFrame;
import com.elfmcys.ysm.format.parser.pojo.animation.keyframe.InstructionKeyFrame;
import com.elfmcys.ysm.format.parser.pojo.animation.value.UnionValue;
import com.elfmcys.ysm.model.resource.client.render.AnimationProtoMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnimationBuilderTest {
    @Test
    void preservesSoundKeyframeValuesAndSourceOrder() {
        var source = new AnimationFile();
        var animation = new Animation();
        animation.animationName = "sound";
        animation.soundKeyFrames.add(frame("minecraft:bell", 1.25f));
        animation.soundKeyFrames.add(frame("模型:铃", 1.25f));
        source.animations.add(animation);

        var proto = AnimationBuilder.build(source).animations().get(0);

        assertEquals(2, proto.soundKeyframes().size());
        assertEquals("minecraft:bell", proto.soundKeyframes().get(0).data());
        assertEquals("模型:铃", proto.soundKeyframes().get(1).data());
        assertEquals(1.25f, proto.soundKeyframes().get(0).startTick());
        assertEquals(1.25f, proto.soundKeyframes().get(1).startTick());

        var runtime = AnimationProtoMapper.animation(proto);
        assertEquals(2, runtime.soundKeyFrames.size());
        assertEquals("minecraft:bell", runtime.soundKeyFrames.get(0).getEventData());
        assertEquals("模型:铃", runtime.soundKeyFrames.get(1).getEventData());
        assertEquals(1.25f, runtime.soundKeyFrames.get(0).getStartTick());
        assertEquals(1.25f, runtime.soundKeyFrames.get(1).getStartTick());
    }

    @Test
    void leavesSoundKeyframesAbsentWhenRawAnimationHasNone() {
        var source = new AnimationFile();
        source.animations.add(new Animation());

        var proto = AnimationBuilder.build(source).animations().get(0);

        assertEquals(0, proto.soundKeyframes().size());
        assertEquals(0, AnimationProtoMapper.animation(proto).soundKeyFrames.size());
    }

    @Test
    void carriesExpressionsAsSourceEnvelopesAcrossTheRoundTrip() {
        var source = new AnimationFile();
        var animation = new Animation();
        animation.animationName = "expression";
        animation.blendWeight = stringValue("v.blend");
        var instructions = new InstructionKeyFrame();
        instructions.startTick = 2.5f;
        instructions.eventData.add("v.a=1");
        instructions.eventData.add("v.b=2;");
        animation.customInstructionKeyframes.add(instructions);
        source.animations.add(animation);

        var proto = AnimationBuilder.build(source).animations().get(0);

        assertEquals("v.blend", proto.blendWeight().orElseThrow().program().source());
        assertEquals(0, proto.blendWeight().orElseThrow().program().format());
        assertEquals(1, proto.instructionKeyframes().size());
        assertEquals("v.a=1;\nv.b=2;\n",
                proto.instructionKeyframes().get(0).programs().source());
        assertEquals(2.5f, proto.instructionKeyframes().get(0).startTick());

        var runtime = AnimationProtoMapper.animation(proto);
        assertNotNull(runtime.blendWeight);
        assertEquals(1, runtime.customInstructionKeyframes.size());
        assertEquals(2.5f, runtime.customInstructionKeyframes.get(0).getStartTick());
        assertEquals(1, runtime.customInstructionKeyframes.get(0).getEventData().length);
    }

    private static UnionValue stringValue(String value) {
        var union = new UnionValue();
        union.type = UnionValue.ValueType.STRING;
        union.stringValue = value;
        return union;
    }

    private static EventKeyFrame frame(String data, float startTick) {
        var frame = new EventKeyFrame();
        frame.eventData = data;
        frame.startTick = startTick;
        return frame;
    }
}
