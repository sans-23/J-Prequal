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

    // Prequal Parameters
    private static final int POOL_SIZE = 16;
    private static final int R_PROBE = 3;
    private static final double Q_RIF_QUANTILE = 0.8;

    // Executor for async probing
    // private final ExecutorService probeExecutor =
    // Executors.newVirtualThreadPerTaskExecutor();

    // ThreadLocal State per Client Thread
    private final ThreadLocal<ClientState> clientState = ThreadLocal.withInitial(ClientState::new);

    private static class ClientState {
        final List<ProbeResult> pool = new ArrayList<>(POOL_SIZE + R_PROBE);
        final Queue<ProbeResult> inbox = new ConcurrentLinkedQueue<>();
        final Random random = new Random();
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

        // 1. Drain Inbox to Pool
        ProbeResult inboxItem;
        while ((inboxItem = state.inbox.poll()) != null) {
            addProbeToPool(state.pool, inboxItem);
        }

        // 2. Trigger Async Probes
        triggerAsyncProbes(state);

        // 3. Select from Pool
        ServerNode selected;
        if (state.pool.size() < 2) {
            selected = servers.get(state.random.nextInt(servers.size()));
        } else {
            selected = applyHCLRule(state.pool);
        }

        return selected;
    }

    private void triggerAsyncProbes(ClientState state) {
        // Capture inbox reference for closure
        Queue<ProbeResult> inbox = state.inbox;

        for (int i = 0; i < R_PROBE; i++) {
            ServerNode target = servers.get(state.random.nextInt(servers.size()));
            // OPTIMIZATION: In this in-memory simulation, probing is non-blocking (tryLock)
            // and significantly
            // faster than scheduling a Virtual Thread. We run it inline to simulate
            // satisfying "IO offload".
            ProbeResult result = target.probe();
            inbox.add(result);
        }
    }

    private void addProbeToPool(List<ProbeResult> pool, ProbeResult result) {
        while (pool.size() >= POOL_SIZE) {
            pool.remove(0); // Removing oldest
        }
        pool.add(result);
    }

    private ServerNode applyHCLRule(List<ProbeResult> pool) {
        // HCL Logic on local pool (No lock needed)
        List<Integer> rifs = new ArrayList<>();
        for (ProbeResult p : pool)
            rifs.add(p.rif());
        Collections.sort(rifs);

        int index = (int) Math.ceil(Q_RIF_QUANTILE * (rifs.size() - 1));
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

        pool.remove(best);
        return best.server();
    }

    @Override
    public String getName() {
        return "Prequal (Async HCL - ThreadLocal)";
    }
}
