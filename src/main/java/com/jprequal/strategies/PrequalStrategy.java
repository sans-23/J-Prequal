package com.jprequal.strategies;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PrequalStrategy implements LoadBalancer {
    private final List<ServerNode> servers = new ArrayList<>();
    private final Random random = new Random();
    private static final int PROBE_COUNT = 4; // Power-of-d choices, d=4

    @Override
    public void addServer(ServerNode server) {
        servers.add(server);
    }

    @Override
    public ServerNode selectServer() {
        if (servers.isEmpty())
            return null;

        ServerNode bestServer = null;
        long bestLatency = Long.MAX_VALUE;

        for (int i = 0; i < PROBE_COUNT; i++) {
            ServerNode candidate = servers.get(random.nextInt(servers.size()));

            // "Estimation": Ask for Estimated Latency (Probe)
            // This is the key difference: Prequal detects the GC pause.
            long latency = candidate.getEstimatedLatency();

            if (latency < bestLatency) {
                bestLatency = latency;
                bestServer = candidate;
            }
        }

        return bestServer;
    }

    @Override
    public String getName() {
        return "Prequal (Power-of-4 Probing)";
    }
}
