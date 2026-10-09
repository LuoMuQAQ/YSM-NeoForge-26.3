package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.session.server.ServerModelSession;
import com.elfmcys.ysm.model.session.server.state.Selection;
import com.elfmcys.ysm.proto.network.AnimationState;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.PlayerStateReport;
import com.elfmcys.ysm.proto.network.StateWriteMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionProtocolHandlerContractTest {
    @Test
    void reportEligibilityFollowsTheSinglePendingSessionTransition() {
        var pending = new ServerModelSession(CatalogSnapshot::empty, false);
        var full = report(StateWriteMode.STATE_WRITE_MODE_FULL);

        assertFalse(pending.acceptsPlayerStateReport(full));
        assertEquals(ServerModelSession.SelectionResult.INVALID_REQUEST,
                pending.select(new Selection.IntrinsicDefault()));
        assertTrue(pending.activate());
        assertFalse(pending.activate());
        assertFalse(pending.hasPublishedCatalog());
        assertTrue(pending.acceptsPlayerStateReport(full));
        assertEquals(ServerModelSession.SelectionResult.ACCEPTED,
                pending.select(new Selection.IntrinsicDefault()));

        var declined = new ServerModelSession(CatalogSnapshot::empty, false);
        assertTrue(declined.decline());
        assertFalse(declined.decline());
        assertFalse(declined.activate());
        assertFalse(declined.acceptsPlayerStateReport(full));
        assertEquals(ServerModelSession.SelectionResult.INVALID_REQUEST,
                declined.select(new Selection.IntrinsicDefault()));
    }

    private static PlayerStateReport report(
            StateWriteMode mode) {
        return PlayerStateReport.newBuilder()
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(1).build())
                .setMode(mode)
                .setAnimation(AnimationState.newBuilder()
                        .setStopped(true).build())
                .build();
    }
}
