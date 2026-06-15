package com.trainqueue.scheduler.core;

/**
 * Tracks available cpu/memory. Not synchronized itself — the {@link Scheduler}
 * mutates it only while holding its placement lock.
 */
public class ResourcePool {

    private final int totalCpuMillis;
    private final int totalMemMb;
    private int availableCpuMillis;
    private int availableMemMb;

    public ResourcePool(int totalCpuMillis, int totalMemMb) {
        this.totalCpuMillis = totalCpuMillis;
        this.totalMemMb = totalMemMb;
        this.availableCpuMillis = totalCpuMillis;
        this.availableMemMb = totalMemMb;
    }

    public boolean fits(int cpuMillis, int memMb) {
        return cpuMillis <= availableCpuMillis && memMb <= availableMemMb;
    }

    /** A job this large can never run on this pool, no matter how idle. */
    public boolean exceedsCapacity(int cpuMillis, int memMb) {
        return cpuMillis > totalCpuMillis || memMb > totalMemMb;
    }

    public void reserve(int cpuMillis, int memMb) {
        availableCpuMillis -= cpuMillis;
        availableMemMb -= memMb;
    }

    public void release(int cpuMillis, int memMb) {
        availableCpuMillis = Math.min(totalCpuMillis, availableCpuMillis + cpuMillis);
        availableMemMb = Math.min(totalMemMb, availableMemMb + memMb);
    }

    public int availableCpuMillis() {
        return availableCpuMillis;
    }

    public int availableMemMb() {
        return availableMemMb;
    }
}
