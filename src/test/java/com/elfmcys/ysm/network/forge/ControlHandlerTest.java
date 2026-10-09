package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.network.protocol.StarredModelSnapshots;
import com.elfmcys.ysm.proto.network.StarredModelsSnapshot;
import java.nio.ByteBuffer;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ControlHandlerTest {
    @Test
    void absentStarredModelHashesAreAnEmptySet() {
        var snapshot = StarredModelSnapshots.create(Set.of());

        assertEquals(0, snapshot.modelHashes().size());
        assertEquals(Set.of(), ControlHandler.readHashSet(snapshot.modelHashes()));
    }

    @Test
    void presentModelHashesRoundTrip() {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = 1;
        var hash = new Hash256(bytes);
        var snapshot = StarredModelSnapshots.create(Set.of(hash));

        assertEquals(Set.of(hash), ControlHandler.readHashSet(snapshot.modelHashes()));
    }

    @Test
    void invalidModelHashRejectsTheWholeSnapshot() {
        var snapshot = StarredModelsSnapshot.newBuilder()
                .addModelHashes(ByteBuffer.wrap(new byte[Hash256.SIZE]))
                .addModelHashes(ByteBuffer.wrap(new byte[Hash256.SIZE - 1]))
                .build();

        assertNull(ControlHandler.readHashSet(snapshot.modelHashes()));
    }
}
