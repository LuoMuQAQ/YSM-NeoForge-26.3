package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.proto.mixel.asset.model.data.BoneAnimation;
import com.elfmcys.ysm.proto.mixel.asset.model.data.BoneKeyFrame;
import com.elfmcys.ysm.proto.mixel.asset.model.data.EasingType;
import com.elfmcys.ysm.proto.mixel.asset.model.data.EventKeyFrame;
import com.elfmcys.ysm.proto.mixel.asset.model.data.ExpressionValue;
import com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType;
import com.elfmcys.ysm.util.ProtoUtil;
import java.io.IOException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import us.hebi.quickbuf.ProtoSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationProtoMapperTest {
    @Test
    void treatsAbsentPostValuesAsAContiguousKeyframe() {
        var frame = BoneKeyFrame.newBuilder()
                .setStartTick(0)
                .setEasingType(EasingType.EASING_TYPE_LINEAR)
                .addPre(ExpressionValue.newBuilder().setNum(1).build())
                .build();
        var bone = BoneAnimation.newBuilder()
                .setBoneName("root")
                .addPosition(frame)
                .build();
        var animation = Animation.newBuilder()
                .setName("test")
                .setLength(0)
                .setLoop(LoopType.LOOP_TYPE_PLAY_ONCE)
                .addBoneAnimations(bone)
                .build();

        assertDoesNotThrow(() -> AnimationProtoMapper.animation(animation));
    }

    @Test
    void matchesFrozenFieldSevenWireAndRuntimeOrder() throws Exception {
        var animation = Animation.newBuilder()
                .setName("sound")
                .setLength(0)
                .setLoop(LoopType.LOOP_TYPE_PLAY_ONCE)
                .setBlendWeight(ExpressionValue.newBuilder().build())
                .addSoundKeyframes(EventKeyFrame.newBuilder()
                        .setData("模型:铃").setStartTick(0).build())
                .addSoundKeyframes(EventKeyFrame.newBuilder()
                        .setData("").setStartTick(1.25f).build())
                .addSoundKeyframes(EventKeyFrame.newBuilder()
                        .setData("").setStartTick(Float.NaN).build())
                .build();

        assertArrayEquals(CANONICAL_FIELD_SEVEN_WIRE, ProtoUtil.serializeToArray(animation));

        var parsed = Animation.parseFrom(FROZEN_FIELD_SEVEN_WIRE);
        var runtime = AnimationProtoMapper.animation(parsed);
        assertEquals(3, runtime.soundKeyFrames.size());
        assertEquals("模型:铃", runtime.soundKeyFrames.get(0).getEventData());
        assertEquals("", runtime.soundKeyFrames.get(1).getEventData());
        assertEquals(0.0f, runtime.soundKeyFrames.get(0).getStartTick());
        assertEquals(1.25f, runtime.soundKeyFrames.get(1).getStartTick());
        assertEquals("", runtime.soundKeyFrames.get(2).getEventData());
        assertTrue(Float.isNaN(runtime.soundKeyFrames.get(2).getStartTick()));
    }

    @Test
    void readsPreFieldSevenPayloadAsAnEmptyList() throws Exception {
        var parsed = Animation.parseFrom(
                HexFormat.of().parseHex("0a05736f756e6415000000001802"));

        assertEquals("sound", parsed.name());
        assertEquals(0, parsed.soundKeyframes().size());
        assertEquals(0, AnimationProtoMapper.animation(parsed).soundKeyFrames.size());
    }

    @Test
    void preFieldSevenReaderSkipsUnknownSoundKeyframes() throws Exception {
        var current = Animation.newBuilder()
                .setName("sound")
                .setLength(3.5f)
                .setLoop(LoopType.LOOP_TYPE_PLAY_ONCE)
                .addSoundKeyframes(EventKeyFrame.newBuilder()
                        .setData("minecraft:bell").setStartTick(1.25f).build())
                .build();

        var old = readPreFieldSeven(ProtoUtil.serializeToArray(current));

        assertEquals("sound", old.name());
        assertEquals(3.5f, old.length());
    }

    private static PreFieldSevenAnimation readPreFieldSeven(byte[] bytes)
            throws IOException {
        var source = ProtoSource.newInstance(bytes);
        var name = "";
        var length = 0.0f;
        for (int tag; (tag = source.readTag()) != 0; ) {
            switch (tag) {
                case 10 -> name = source.readString();
                case 21 -> length = source.readFloat();
                default -> source.skipField(tag);
            }
        }
        return new PreFieldSevenAnimation(name, length);
    }

    private record PreFieldSevenAnimation(String name, float length) {
    }

    private static final byte[] FROZEN_FIELD_SEVEN_WIRE = HexFormat.of().parseHex(
            "0a05736f756e641500000000180222003a110a0ae6a8a1e59e8b3ae993831500000000"
                    + "3a070a00150000a03f3a070a00150000c07f");
    private static final byte[] CANONICAL_FIELD_SEVEN_WIRE = HexFormat.of().parseHex(
            "0a05736f756e64180222003a0c0a0ae6a8a1e59e8b3ae993833a05150000a03f"
                    + "3a05150000c07f");
}
