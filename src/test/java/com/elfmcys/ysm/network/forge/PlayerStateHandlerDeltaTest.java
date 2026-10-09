package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.proto.network.AnimationState;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.ModelReference;
import com.elfmcys.ysm.proto.network.ModelSelectionState;
import com.elfmcys.ysm.proto.network.PlayerStateUpdate;
import com.elfmcys.ysm.proto.network.RoamingState;
import com.elfmcys.ysm.proto.network.StateWriteMode;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateHandlerDeltaTest {
    @Test
    void deltaFactoryBuildsAContentDrivenUpdate() {
        var update = PlayerStateHandler.newDelta(42)
                .setAnimation(AnimationState.newBuilder()
                        .setStopped(true).build())
                .build();

        assertEquals(42, update.subject().entityId());
        assertEquals(StateWriteMode.STATE_WRITE_MODE_DELTA,
                update.mode());
    }

    @Test
    void roamingAbsenceResetsFullAndLeavesDeltaUnchanged() {
        var full = PlayerStateUpdate.newBuilder()
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(42).build())
                .setMode(StateWriteMode.STATE_WRITE_MODE_FULL)
                .setModel(ModelSelectionState.newBuilder()
                        .setModel(ModelReference.newBuilder()
                                .setBuiltinDefault(true).build()).build())
                .build();

        var reset = PlayerStateHandler.roamingApplication(full);

        assertNotNull(reset);
        assertTrue(reset.full());
        assertNull(reset.modelKey());
        assertTrue(reset.values().isEmpty());
        assertNull(PlayerStateHandler.roamingApplication(PlayerStateHandler.newDelta(42).build()));
    }

    @Test
    void selfFullBindsTheRoamingNamespaceOfItsSectionOrItsOwnModel() {
        var hash = hash(9);
        var full = PlayerStateUpdate.newBuilder()
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(42).build())
                .setMode(StateWriteMode.STATE_WRITE_MODE_FULL)
                .setModel(ModelSelectionState.newBuilder()
                        .setModel(ModelReference.newBuilder()
                                .setModelHash(ByteBuffer.wrap(hash.bytes())).build())
                        .build())
                .build();

        var bound = PlayerStateHandler.selfFullRoamingKey(full, hash);

        assertNotNull(bound);
        assertEquals(hash.roamingHash(), bound.intValue());

        var withSection = full.toBuilder()
                .setRoaming(RoamingState.newBuilder()
                        .setModelKey(77).build())
                .build();
        assertEquals(77, PlayerStateHandler.selfFullRoamingKey(withSection, hash).intValue());

        var builtinDefault = PlayerStateHandler.selfFullRoamingKey(full, null);

        assertNull(builtinDefault);
    }

    @Test
    void builtinDefaultAppliesAsTheConcreteLocalModelIdentity() {
        var selected = hash(10);
        var builtinDefault = hash(11);

        assertEquals(selected,
                PlayerStateHandler.appliedPlayerModelHash(selected, builtinDefault));
        assertEquals(builtinDefault,
                PlayerStateHandler.appliedPlayerModelHash(null, builtinDefault));
    }

    private static Hash256 hash(int seed) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) seed;
        return new Hash256(bytes);
    }
}
