package com.jprequal.core;

public record SimulationConfig(
        int numServers,
        int numClients,
        int numRequests,
        int gcPauseMs,
        int gcIntervalMs,
        int baseLatencyMs,
        int prequalProbeCount,
        int prequalPoolSize,
        double prequalQuantile,
        String experimentName) {
    public static SimulationConfig defaults() {
        return new SimulationConfig(
                1000, // numServers
                2000, // numClients
                1_000_000, // numRequests
                50, // gcPauseMs
                100, // gcIntervalMs
                10, // baseLatencyMs
                3, // prequalProbeCount (d)
                16, // prequalPoolSize
                0.8, // prequalQuantile
                "DEFAULT");
    }
}
