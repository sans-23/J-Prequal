package com.jprequal.core;

public interface LoadBalancer {
    void addServer(ServerNode server);
    ServerNode selectServer();
    String getName();
}
