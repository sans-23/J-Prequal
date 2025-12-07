package com.jprequal.strategies;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class P2CLeastConnectionsStrategy implements LoadBalancer {
    private final List<ServerNode> servers = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void addServer(ServerNode server) {
        servers.add(server);
    }

    @Override
    public ServerNode selectServer() {
        if (servers.isEmpty()) {
            return null;
        }

        // Pick two random servers
        int size = servers.size();
        ServerNode s1 = servers.get(random.nextInt(size));
        ServerNode s2 = servers.get(random.nextInt(size));

        // Return the one with fewer active requests
        if (s1.getActiveRequests() <= s2.getActiveRequests()) {
            return s1;
        } else {
            return s2;
        }
    }

    @Override
    public String getName() {
        return "P2C Least Connections";
    }
}
