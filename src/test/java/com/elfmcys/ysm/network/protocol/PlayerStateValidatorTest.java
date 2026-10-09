package com.elfmcys.ysm.network.protocol;

import com.elfmcys.ysm.proto.network.AnimationState;
import com.elfmcys.ysm.proto.network.EffectState;
import com.elfmcys.ysm.proto.network.EffectStateSet;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.GameplayState;
import com.elfmcys.ysm.proto.network.ModelReference;
import com.elfmcys.ysm.proto.network.ModelSelectionState;
import com.elfmcys.ysm.proto.network.MolangVariable;
import com.elfmcys.ysm.proto.network.PlayerStateReport;
import com.elfmcys.ysm.proto.network.PlayerStateUpdate;
import com.elfmcys.ysm.proto.network.RoamingState;
import com.elfmcys.ysm.proto.network.StateWriteMode;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateValidatorTest {
    @Test
    void acceptsBoundedFullReport() {
        var report = baseReport()
                .setGameplay(GameplayState.newBuilder()
                        .setHealth(20).setMaxHealth(20).setFoodLevel(20).build())
                .setAnimation(AnimationState.newBuilder()
                        .setStopped(true).build())
                .setRoaming(RoamingState.newBuilder().setModelKey(0)
                        .addVariables(MolangVariable.newBuilder()
                                .setName("speed").setValue(1.0f).build()).build())
                .build();

        assertTrue(PlayerStateValidator.validReport(report));
    }

    @Test
    void rejectsUnboundedOrSemanticallyInvalidValues() {
        assertFalse(PlayerStateValidator.validReport(baseReport().setGameplay(
                GameplayState.newBuilder()
                        .setHealth(21).setMaxHealth(20).build()).build()));
        assertFalse(PlayerStateValidator.validReport(baseReport().setAnimation(
                AnimationState.newBuilder()
                        .setAnimationId(" ").build()).build()));
        assertFalse(PlayerStateValidator.validReport(baseReport().setRoaming(
                RoamingState.newBuilder().setModelKey(0).addVariables(
                        MolangVariable.newBuilder()
                                .setName("value").setValue(Float.NaN).build()).build()).build()));

        var effects = EffectStateSet.newBuilder()
                .addEffects(EffectState.newBuilder()
                        .setEffectId("minecraft:speed").setLevel(1).build())
                .addEffects(EffectState.newBuilder()
                        .setEffectId("minecraft:speed").setLevel(2).build())
                .build();
        assertFalse(PlayerStateValidator.validReport(baseReport().setEffects(effects).build()));
    }

    @Test
    void fullUpdateRequiresModelAndNeverAcceptsPlayerIdFromWire() {
        var update = PlayerStateUpdate.newBuilder()
                .setMode(StateWriteMode.STATE_WRITE_MODE_FULL)
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(1).build());
        assertFalse(PlayerStateValidator.validUpdate(update.build()));

        update.setModel(ModelSelectionState.newBuilder()
                .setModel(ModelReference.newBuilder()
                        .setBuiltinDefault(true).build())
                .setTextureId("").setDisabled(false).build());
        var valid = update.build();
        assertTrue(PlayerStateValidator.validUpdate(valid));
        var playerSubject = valid.subject().withPlayerId(
                ByteBuffer.wrap(new byte[PlayerId.SIZE]));
        assertFalse(PlayerStateValidator.validUpdate(valid.withSubject(playerSubject)));
    }

    private static PlayerStateReport.Builder baseReport() {
        return PlayerStateReport.newBuilder()
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(1).build())
                .setMode(StateWriteMode.STATE_WRITE_MODE_FULL);
    }
}
