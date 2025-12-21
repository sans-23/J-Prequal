package com.jprequal.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Random;

//Simulates a backend server with random STW GC pauses.
public class ServerNode {
    private final String id;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final Random random = new Random();
    private final Thread gcThread;
    private volatile boolean running = true;

    private final int baseLatencyMs;
    private final int gcPauseMs;
    private final int gcIntervalMs;

    // RW lock to simulate STW - requests take read lock, GC takes write lock
    private final ReentrantReadWriteLock stwLock = new ReentrantReadWriteLock();

    public ServerNode(String id, SimulationConfig config) {
        this(id, config.baseLatencyMs(), config);
    }

    public ServerNode(String id, int baseLatencyMs, SimulationConfig config) {
        this.id = id;
        this.baseLatencyMs = baseLatencyMs;
        this.gcPauseMs = config.gcPauseMs();
        this.gcIntervalMs = config.gcIntervalMs();

        // Background thread for GC simulation
        this.gcThread = Thread.ofVirtual().unstarted(() -> {
            while (running) {
                try {

                    Thread.sleep(gcIntervalMs + random.nextInt(1000));

                    performGcPause();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        this.gcThread.start();
    }

    public String getId() {
        return id;
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public void handleRequest() {
        activeRequests.incrementAndGet();
        try {
            // blocks if GC is running
            stwLock.readLock().lock();
            try {
                Thread.sleep(baseLatencyMs + random.nextInt(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                stwLock.readLock().unlock();
            }

        } finally {
            activeRequests.decrementAndGet();
        }
    }

    private void performGcPause() {
        stwLock.writeLock().lock();
        try {
            // STW pause
            Thread.sleep(gcPauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stwLock.writeLock().unlock();
        }
    }

    // Non-blocking probe - returns huge latency if server is mid-GC.
    public com.jprequal.strategies.ProbeResult probe() {

        if (!stwLock.readLock().tryLock()) {
            // stalled - return 10s penalty
            return new com.jprequal.strategies.ProbeResult(this, activeRequests.get(), 10_000_000_000L,
                    System.nanoTime());
        }
        try {
            long latencyEst = (long) activeRequests.get() * baseLatencyMs * 1_000_000L; // ns
            return new com.jprequal.strategies.ProbeResult(this, activeRequests.get(), latencyEst, System.nanoTime());
        } finally {
            stwLock.readLock().unlock();
        }
    }

    // Simplified probe returning estimated latency in ms.
    public int getEstimatedLatency() {
        if (!stwLock.readLock().tryLock()) {
            return 10000; // stalled
        }
        try {
            return activeRequests.get() * baseLatencyMs;
        } finally {
            stwLock.readLock().unlock();
        }
    }

    public long getEstimatedPendingWork() {
        return getEstimatedLatency();
    }

    public void stop() {
        running = false;
        gcThread.interrupt();
    }
}
