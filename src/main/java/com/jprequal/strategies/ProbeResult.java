package com.jprequal.strategies;

import com.jprequal.core.ServerNode;

public record ProbeResult(ServerNode server, int rif, long latencyNs, long timestamp) {
}
