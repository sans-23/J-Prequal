package com.jprequal;

import com.jprequal.core.SimulationConfig;
import com.jprequal.strategies.LeastConnectionsStrategy;
import com.jprequal.strategies.PrequalStrategy;
import com.jprequal.strategies.RoundRobinStrategy;

import java.util.ArrayList;
import java.util.List;

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
                        Simulator.run(exp.name(), config, new RoundRobinStrategy(), "RoundRobin", writer);
                        Simulator.run(exp.name(), config, new LeastConnectionsStrategy(), "LeastConnections", writer);
                        Simulator.run(exp.name(), config, new com.jprequal.strategies.P2CLeastConnectionsStrategy(),
                                "P2CLeastConnections", writer);
                    }

                    Simulator.run(exp.name(), config, new PrequalStrategy(config), "Prequal", writer);
                    writer.flush();
                }
            }
        }
    }
}
