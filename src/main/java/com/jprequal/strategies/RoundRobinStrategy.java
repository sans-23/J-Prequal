package com.jprequal.strategies;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements LoadBalancer {
    private final List<ServerNode> servers = new ArrayList<>();
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public void addServer(ServerNode server) {
        servers.add(server);
    }

    @Override
    public ServerNode selectServer() {
        if (servers.isEmpty())
            return null;

        int i = index.getAndIncrement();
        if (i < 0) { // Handle overflow
            i = 0;
            index.set(0);
        }
        return servers.get(i % servers.size());
    }

    @Override
    public String getName() {
        return "Round Robin";
    }
}
