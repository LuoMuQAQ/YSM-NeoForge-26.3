package com.elfmcys.ysm.network.protocol;

import com.elfmcys.ysm.proto.network.AnimationState;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.PlayerStateReport;
import com.elfmcys.ysm.proto.network.RoamingState;
import com.elfmcys.ysm.proto.network.StateWriteMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateReportPolicyTest {
    private final PlayerStateReportPolicy policy = new PlayerStateReportPolicy(
            Set.of(PlayerStateSection.ANIMATION), 50, 30_000);

    @Test
    void acceptsOnlyNegotiatedSections() {
        var animation = report(StateWriteMode.STATE_WRITE_MODE_DELTA)
                .setAnimation(AnimationState.newBuilder()
                        .setStopped(true).build()).build();
        var roaming = report(StateWriteMode.STATE_WRITE_MODE_DELTA)
                .setRoaming(RoamingState.newBuilder()
                        .setModelKey(1).build()).build();

        assertTrue(policy.acceptsProjection(animation));
        assertFalse(policy.acceptsProjection(roaming));
    }

    @Test
    void fullMayOmitARequestedSectionButDeltaMayNotBeEmpty() {
        assertTrue(policy.acceptsProjection(
                report(StateWriteMode.STATE_WRITE_MODE_FULL).build()));
        assertFalse(policy.acceptsProjection(
                report(StateWriteMode.STATE_WRITE_MODE_DELTA).build()));
    }

    private static PlayerStateReport.Builder report(StateWriteMode mode) {
        return PlayerStateReport.newBuilder()
                .setSubject(EntityRef.newBuilder()
                        .setEntityId(1).build())
                .setMode(mode);
    }
}
