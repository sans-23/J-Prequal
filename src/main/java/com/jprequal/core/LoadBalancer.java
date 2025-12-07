package com.jprequal.core;

import java.util.List;

public interface LoadBalancer {
    void addServer(ServerNode server);
    ServerNode selectServer();
    String getName();
}
