package com.elfmcys.ysm.network.protocol;

import java.util.Objects;

public record NegotiatedSessionPolicy(PlayerStateReportPolicy stateReportPolicy) {
    public NegotiatedSessionPolicy {
        Objects.requireNonNull(stateReportPolicy, "stateReportPolicy");
    }
}
