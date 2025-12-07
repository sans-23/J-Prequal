# J-Prequal: Advanced Load Balancing Simulation

## Overview
This project is a high-fidelity simulation and analysis of the **Prequal** (Probing to Reduce Queuing and Latency) load balancing algorithm. It benchmarks Prequal against industry standard strategies—**Round Robin**, **Least Connections**, and **Power-of-2-Choices (P2C)**—specifically focusing on tail latency reduction in heterogeneous server environments with "Stop-the-World" Garbage Collection (GC) pauses.

## Key Features
-   **Strategies Implemented**:
    -   `PrequalStrategy`: The core algorithm using asynchronous probing and Hot-Cold Lexicographic (HCL) selection.
    -   `LeastConnectionsStrategy`: An O(N) "Oracle" baseline (scans all servers).
    -   `P2CLeastConnectionsStrategy`: A scalable O(1) baseline (samples 2 servers).
    -   `RoundRobinStrategy`: Simple cyclical distribution.
-   **Realistic Simulation**:
    -   **Heterogeneity**: Supports mixed clusters of "Fast" (10ms) and "Slow" (100ms) servers.
    -   **GC Pauses**: Simulates random STW events (50ms) that block request processing.
    -   **Concurrency**: Uses Java Virtual Threads to simulate thousands of concurrent clients.
-   **Analysis Suite**:
    -   Automated parameter sweeps (Concurrency, Probe Count, Quantile, Pool Size).
    -   Python-based plotting engine for generating professional charts.

## Directory Structure
```
J-Prequal/
├── src/main/java/com/jprequal/  # Core Source Code
│   ├── core/                    # ServerNode, LoadBalancer interfaces
│   ├── strategies/              # Prequal, RoundRobin, P2C, etc.
│   └── SimulationRunner.java    # Main entry point for experiments
├── analysis/                    # Analysis Tools & Output
│   ├── plot_results.py          # Python plotting script
│   ├── simulation_results.csv   # Generated data (Single Source of Truth)
│   └── plots/                   # Generated graphs
└── pom.xml                      # Maven Build Definitions
```

## Prerequisites
-   **Java 21+**: Required for Virtual Threads.
-   **Maven**: For building the project.
-   **Python 3**: For running the analysis script.

## Quick Start

### 1. Build and Run Simulation
The simulation will run a comprehensive suite of experiments (Concurrency, Probe Count, Heterogeneity) and output data to `analysis/simulation_results.csv`.

```bash
mvn clean compile exec:java -Dexec.mainClass="com.jprequal.SimulationRunner"
```
*Note: This may take a few minutes as it simulates over 500,000 requests across multiple configurations.*

### 2. Generate Analysis Plots
Use the provided Python script to visualize the results. It will create a virtual environment and install necessary dependencies (`pandas`, `matplotlib`, `seaborn`).

```bash
cd analysis
python3 -m venv venv
source venv/bin/activate
pip install pandas matplotlib seaborn
python3 plot_results.py
```

### 3. View Results
Open the `analysis/plots/` directory to see the generated graphs, such as:
-   `HeterogeneitySweep_p99.png`: Demonstrates Prequal's superiority over P2C in mixed environments.
-   `ConcurrencySweep_throughput.png`: Shows system scalability.

## Key Findings
1.  **Heterogeneity Wins**: In a mixed environment (50% fast, 50% slow servers), **Prequal significantly outperforms P2C Least Connections** in P99 latency. Prequal's active probing identifies and favors the fast servers, whereas P2C (based only on connection count) naively distributes load equally, punishing the tail.
2.  **GC Resilience**: Prequal effectively avoids servers undergoing GC pauses, maintaining a flat tail latency curve where Round Robin spikes.
3.  **Scalability**: Prequal matches the throughput of the O(N) Oracle Least Connections without the scalability bottleneck of scanning the entire fleet.

## License
MIT License
