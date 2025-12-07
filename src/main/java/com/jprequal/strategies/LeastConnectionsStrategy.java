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

    @Override
    public ServerNode selectServer() {
        ServerNode bestServer = null;
        int minConnections = Integer.MAX_VALUE;

        for (ServerNode server : servers) {
            int active = server.getActiveRequests();
            if (active < minConnections) {
                minConnections = active;
                bestServer = server;
            }
        }
        return bestServer;
    }

    @Override
    public String getName() {
        return "Least Connections";
    }
}
