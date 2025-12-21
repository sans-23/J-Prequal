package com.jprequal.strategies;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PrequalStrategy implements LoadBalancer {
    private final List<ServerNode> servers = new ArrayList<>();

    private final int poolSize;
    private final int rProbe;
    private final double qRifQuantile;

    public PrequalStrategy(com.jprequal.core.SimulationConfig config) {
        this.poolSize = config.prequalPoolSize();
        this.rProbe = config.prequalProbeCount();
        this.qRifQuantile = config.prequalQuantile();
        this.clientState = ThreadLocal.withInitial(() -> new ClientState(poolSize, rProbe));
    }

    private final ThreadLocal<ClientState> clientState;

    private static class ClientState {
        final List<ProbeResult> pool;
        final Queue<ProbeResult> inbox = new ConcurrentLinkedQueue<>();
        final Random random = new Random();

        ClientState(int poolSize, int rProbe) {
            this.pool = new ArrayList<>(poolSize + rProbe);
        }
    }

    @Override
    public void addServer(ServerNode server) {
        servers.add(server);
    }

    @Override
    public ServerNode selectServer() {
        if (servers.isEmpty())
            return null;

        ClientState state = clientState.get();

        // drain async probe results
        ProbeResult inboxItem;
        while ((inboxItem = state.inbox.poll()) != null) {
            addProbeToPool(state.pool, inboxItem);
        }

        triggerAsyncProbes(state);

        // pick from pool or fallback to random
        ServerNode selected;
        if (state.pool.size() < 2) {
            selected = servers.get(state.random.nextInt(servers.size()));
        } else {
            selected = applyHCLRule(state.pool);
        }

        return selected;
    }

    private void triggerAsyncProbes(ClientState state) {
        Queue<ProbeResult> inbox = state.inbox;

        for (int i = 0; i < rProbe; i++) {
            ServerNode target = servers.get(state.random.nextInt(servers.size()));
            // inline probe - non-blocking tryLock is fast enough
            ProbeResult result = target.probe();
            inbox.add(result);
        }
    }

    private void addProbeToPool(List<ProbeResult> pool, ProbeResult result) {
        while (pool.size() >= poolSize) {
            pool.remove(0); // FIFO eviction
        }
        pool.add(result);
    }

    // Hot-Cold Lexicographic selection per paper
    private ServerNode applyHCLRule(List<ProbeResult> pool) {
        List<Integer> rifs = new ArrayList<>();
        for (ProbeResult p : pool)
            rifs.add(p.rif());
        Collections.sort(rifs);

        int index = (int) Math.ceil(qRifQuantile * (rifs.size() - 1));
        int qRif = rifs.get(index);

        List<ProbeResult> hot = new ArrayList<>();
        List<ProbeResult> cold = new ArrayList<>();

        for (ProbeResult p : pool) {
            if (p.rif() > qRif) {
                hot.add(p);
            } else {
                cold.add(p);
            }
        }

        ProbeResult best;
        if (cold.isEmpty()) {
            best = hot.stream().min(Comparator.comparingInt(ProbeResult::rif)).orElse(pool.get(0));
        } else {
            best = cold.stream().min(Comparator.comparingLong(ProbeResult::latencyNs)).orElse(cold.get(0));
        }

        // pool.remove(best); // optional: probe reuse
        return best.server();
    }

    @Override
    public String getName() {
        return "Prequal (Async HCL - ThreadLocal)";
    }
}
