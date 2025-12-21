package com.jprequal.strategies;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;

import java.util.ArrayList;
import java.util.List;

public class LeastConnectionsStrategy implements LoadBalancer {
    private final List<ServerNode> servers = new ArrayList<>();

    @Override
    public void addServer(ServerNode server) {
        servers.add(server);
    }

    // ~100us per server probe overhead
    private final long scanOverheadNs = 100_000;

    @Override
    public ServerNode selectServer() {
        if (servers.isEmpty())
            return null;

        // O(n) scan cost
        long totalOverheadNs = servers.size() * scanOverheadNs;
        try {
            long millis = totalOverheadNs / 1_000_000;
            int nanos = (int) (totalOverheadNs % 1_000_000);
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ServerNode best = null;
        int minConnections = Integer.MAX_VALUE;

        for (ServerNode server : servers) {
            int connections = server.getActiveRequests();
            if (connections < minConnections) {
                minConnections = connections;
                best = server;
            }
        }
        return best;
    }

    @Override
    public String getName() {
        return "LeastConnections (Full Scan)";
    }
}
