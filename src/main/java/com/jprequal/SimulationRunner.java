package com.jprequal;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;
import com.jprequal.strategies.LeastConnectionsStrategy;
import com.jprequal.strategies.PrequalStrategy;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimulationRunner {

    private static final int NUM_SERVERS = 1000;
    private static final int NUM_REQUESTS = 1_000_000;
    private static final int CONCURRENCY = 2000; // Simultaneous clients

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== J-Prequal Simulation ===");

        System.out.println("Running with " + CONCURRENCY + " clients");
        System.out.println("Running with " + NUM_REQUESTS + " requests");
        System.out.println("Running with " + NUM_SERVERS + " servers");

        runSimulation(new com.jprequal.strategies.RoundRobinStrategy());
        System.out.println("--------------------------------------------------");
        runSimulation(new LeastConnectionsStrategy());
        System.out.println("--------------------------------------------------");
        runSimulation(new PrequalStrategy());
    }

    private static void runSimulation(LoadBalancer lb) throws InterruptedException {
        System.out.println("Running Strategy: " + lb.getName());

        // 1. Setup Servers
        List<ServerNode> nodes = new ArrayList<>();
        for (int i = 0; i < NUM_SERVERS; i++) {
            ServerNode node = new ServerNode("Server-" + i);
            lb.addServer(node);
            nodes.add(node);
        }

        // 2. Setup Clients (Bounded Virtual Threads)
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY, Thread.ofVirtual().factory());
        DescriptiveStatistics stats = new DescriptiveStatistics();

        long startBenchmark = System.currentTimeMillis();

        // Warmup Phase (20% of requests, not counted)
        System.out.println("Warming up...");
        int warmupRequests = NUM_REQUESTS / 5;
        for (int i = 0; i < warmupRequests; i++) {
            executor.submit(() -> {
                ServerNode selected = lb.selectServer();
                if (selected != null) {
                    selected.handleRequest();
                }
            });
        }

        // Measurement Phase
        System.out.println("Measuring...");
        for (int i = 0; i < NUM_REQUESTS; i++) {
            executor.submit(() -> {
                long start = System.nanoTime();

                // Select Server
                ServerNode selected = lb.selectServer();
                if (selected != null) {
                    selected.handleRequest();
                }

                long duration = System.nanoTime() - start;
                long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);
                synchronized (stats) {
                    stats.addValue(durationMs);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        long totalTime = System.currentTimeMillis() - startBenchmark;

        // 3. Report
        long slowRequests = 0;
        for (double v : stats.getValues()) {
            if (v > 100)
                slowRequests++;
        }

        System.out.println("Total Time: " + totalTime + " ms");
        System.out.println("Requests Processed: " + stats.getN());
        System.out.println("Slow Requests (>100ms): " + slowRequests);
        System.out.printf("p50 Latency: %.2f ms%n", stats.getPercentile(50));
        System.out.printf("p90 Latency: %.2f ms%n", stats.getPercentile(90));
        System.out.printf("p99 Latency: %.2f ms%n", stats.getPercentile(99));
        System.out.printf("Max Latency: %.2f ms%n", stats.getMax());
    }
}
