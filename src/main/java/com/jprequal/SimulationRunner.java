package com.jprequal;

import com.jprequal.core.LoadBalancer;
import com.jprequal.core.ServerNode;
import com.jprequal.core.SimulationConfig;
import com.jprequal.strategies.LeastConnectionsStrategy;
import com.jprequal.strategies.PrequalStrategy;
import com.jprequal.strategies.RoundRobinStrategy;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimulationRunner {

    record Experiment(String name, List<SimulationConfig> configs) {
    }

    public static void main(String[] args) throws InterruptedException, java.io.IOException {
        System.out.println("=== J-Prequal Simulation (Sweeping Configs) ===");

        new java.io.File("analysis").mkdirs();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter("analysis/simulation_results.csv"))) {
            writer.println("experiment,config,strategy,throughput,p50,p90,p99,p99.9,max,errorRate,slowRequests");
            System.out.println("Writing results to analysis/simulation_results.csv...");

            List<Experiment> experiments = new ArrayList<>();

            // concurrency sweep
            List<SimulationConfig> concurrencyConfigs = new ArrayList<>();
            for (int clients = 50; clients <= 2550; clients += 100) {
                concurrencyConfigs
                        .add(new SimulationConfig(1000, clients, 50_000, 50, 100, 10, 3, 16, 0.8, "ConcurrencySweep"));
            }
            experiments.add(new Experiment("ConcurrencySweep", concurrencyConfigs));

            // probe count (d) sweep
            List<SimulationConfig> probeConfigs = new ArrayList<>();
            for (int d = 2; d <= 10; d++) {
                probeConfigs.add(new SimulationConfig(1000, 2000, 50_000, 50, 100, 10, d, 16, 0.8, "ProbeCountSweep"));
            }
            experiments.add(new Experiment("ProbeCountSweep", probeConfigs));

            // quantile (q) sweep
            List<SimulationConfig> quantileConfigs = new ArrayList<>();
            for (double q = 0.5; q <= 1.0; q += 0.05) {
                quantileConfigs.add(new SimulationConfig(1000, 2000, 50_000, 50, 100, 10, 3, 16, q, "QuantileSweep"));
            }
            experiments.add(new Experiment("QuantileSweep", quantileConfigs));

            // pool size sweep
            List<SimulationConfig> poolConfigs = new ArrayList<>();
            for (int poolSize = 2; poolSize <= 64; poolSize += 2) {
                poolConfigs
                        .add(new SimulationConfig(1000, 2000, 50_000, 50, 100, 10, 3, poolSize, 0.8, "PoolSizeSweep"));
            }
            experiments.add(new Experiment("PoolSizeSweep", poolConfigs));

            // heterogeneous server latencies (10ms vs 100ms)
            List<SimulationConfig> heteroConfigs = new ArrayList<>();
            for (int clients = 50; clients <= 2550; clients += 100) {
                heteroConfigs.add(
                        new SimulationConfig(1000, clients, 50_000, 50, 100, 10, 3, 16, 0.8, "HeterogeneitySweep"));
            }
            experiments.add(new Experiment("HeterogeneitySweep", heteroConfigs));

            for (Experiment exp : experiments) {
                System.out.println("Running Experiment: " + exp.name());
                for (SimulationConfig config : exp.configs()) {
                    System.out.println("  Config: " + config);
                    // skip baselines for prequal-only parameter tuning
                    boolean runBaselines = !exp.name().equals("ProbeCountSweep") &&
                            !exp.name().equals("QuantileSweep") &&
                            !exp.name().equals("PoolSizeSweep");

                    if (runBaselines) {
                        runSimulation(exp.name(), config, new RoundRobinStrategy(), "RoundRobin", writer);
                        runSimulation(exp.name(), config, new LeastConnectionsStrategy(), "LeastConnections", writer);
                        runSimulation(exp.name(), config, new com.jprequal.strategies.P2CLeastConnectionsStrategy(),
                                "P2CLeastConnections", writer);
                    }

                    runSimulation(exp.name(), config, new PrequalStrategy(config), "Prequal", writer);
                    writer.flush();
                }
            }
        }
    }

    private static void runSimulation(String experimentName, SimulationConfig config, LoadBalancer lb,
            String strategyName, java.io.PrintWriter writer)
            throws InterruptedException {
        // setup
        List<ServerNode> nodes = new ArrayList<>();
        boolean isHeterogeneous = "HeterogeneitySweep".equals(experimentName);

        for (int i = 0; i < config.numServers(); i++) {
            int latency = config.baseLatencyMs();
            if (isHeterogeneous && i >= config.numServers() / 2) {
                latency = 100; // slow half
            }

            ServerNode node = new ServerNode("Server-" + i, latency, config);
            lb.addServer(node);
            nodes.add(node);
        }

        ExecutorService executor = Executors.newFixedThreadPool(config.numClients(), Thread.ofVirtual().factory());
        DescriptiveStatistics stats = new DescriptiveStatistics();
        final int numRequests = config.numRequests();

        // warmup (20%)
        int warmupRequests = numRequests / 5;
        try {
            List<java.util.concurrent.Callable<Object>> warmupTasks = new ArrayList<>();
            for (int i = 0; i < warmupRequests; i++) {
                warmupTasks.add(Executors.callable(() -> {
                    ServerNode selected = lb.selectServer();
                    if (selected != null) {
                        selected.handleRequest();
                    }
                }));
            }
            executor.invokeAll(warmupTasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long startTime = System.nanoTime();

        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < numRequests; i++) {
            executor.submit(() -> {
                long start = System.nanoTime();

                ServerNode selected = lb.selectServer();
                if (selected != null) {
                    selected.handleRequest();
                }

                long duration = System.nanoTime() - start;
                long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);
                synchronized (stats) {
                    stats.addValue(durationMs);
                }
                int c = completed.incrementAndGet();
                if (c % 10000 == 0) {
                    System.out.println("Experiment " + experimentName + " [" + strategyName + "]: Finished " + c + "/"
                            + numRequests);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        long totalTimeNs = System.nanoTime() - startTime;
        double totalTimeSec = totalTimeNs / 1_000_000_000.0;
        double throughput = numRequests / totalTimeSec;

        // report
        long slowRequests = 0;
        for (double v : stats.getValues()) {
            if (v > 100)
                slowRequests++;
        }

        double errorRate = 0.0;

        writer.printf("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d%n",
                experimentName,
                config.toString().replace(",", "|"), // Escape csv commas in record toString
                strategyName,
                throughput,
                stats.getPercentile(50),
                stats.getPercentile(90),
                stats.getPercentile(99),
                stats.getPercentile(99.9),
                stats.getMax(),
                errorRate,
                slowRequests);

        // cleanup
        for (ServerNode node : nodes) {
            node.stop();
        }
    }
}
