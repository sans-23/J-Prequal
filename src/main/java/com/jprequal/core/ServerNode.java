package com.jprequal.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Random;

/**
 * Represents a dummy backend server.
 * Simulates random 'Stop-the-World' GC pauses INDEPENDENT of requests.
 */
public class ServerNode {
    private final String id;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final Random random = new Random();
    private final Thread gcThread;
    private volatile boolean running = true;

    // Simulation parameters
    private static final int BASE_LATENCY_MS = 10;
    // 2s pause every ~10s roughly?
    // 2s pause every ~10s roughly?
    // Let's say 5% of time is GC.
    private static final int GC_PAUSE_MS = 50;
    private static final int GC_INTERVAL_MS = 100;

    // Readers (requests) blocked by Writer (GC)
    private final ReentrantReadWriteLock stwLock = new ReentrantReadWriteLock();

    public ServerNode(String id) {
        this.id = id;

        // Start Background GC Simulator (Virtual Thread to allow high scalability)
        this.gcThread = Thread.ofVirtual().unstarted(() -> {
            while (running) {
                try {
                    // Work period
                    Thread.sleep(GC_INTERVAL_MS + random.nextInt(1000));

                    // GC Start
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
            // Processing requires Read Lock.
            // If GC is active (Write Lock), this BLOCKS.
            stwLock.readLock().lock();
            try {
                Thread.sleep(BASE_LATENCY_MS + random.nextInt(5));
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
            // STOP THE WORLD
            Thread.sleep(GC_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stwLock.writeLock().unlock();
        }
    }

    /**
     * Prequal Probe: Returns estimated latency.
     * Simulates a real network probe: if we can't get a read lock immediately,
     * it means server is paused or about to pause (Writer waiting).
     */
    public com.jprequal.strategies.ProbeResult probe() {
        // Simulate network probe
        if (!stwLock.readLock().tryLock()) {
            // Server is stalled/GCing
            return new com.jprequal.strategies.ProbeResult(this, activeRequests.get(), 10_000_000_000L,
                    System.nanoTime()); // 10s penalty
        }
        try {
            long latencyEst = (long) activeRequests.get() * BASE_LATENCY_MS * 1_000_000L; // ns
            return new com.jprequal.strategies.ProbeResult(this, activeRequests.get(), latencyEst, System.nanoTime());
        } finally {
            stwLock.readLock().unlock();
        }
    }

    /**
     * Prequal Probe: Returns estimated latency.
     * Simulates a real network probe: if we can't get a read lock immediately,
     * it means server is paused or about to pause (Writer waiting).
     */
    public int getEstimatedLatency() {
        if (!stwLock.readLock().tryLock()) {
            return 10000; // Probe timed out / Server stalled
        }
        try {
            // Active Requests * Base Latency
            return activeRequests.get() * BASE_LATENCY_MS;
        } finally {
            stwLock.readLock().unlock();
        }
    }

    // For compatibility with PrequalStrategy asking for estimated pending work
    // We can map this to getEstimatedLatency logic
    public long getEstimatedPendingWork() {
        return getEstimatedLatency();
    }

    public void stop() {
        running = false;
        gcThread.interrupt();
    }
}
