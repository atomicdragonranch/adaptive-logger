package io.adaptivelogger.sampling;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Fixed-rate sampler that logs a percentage of messages.
 *
 * Uses ThreadLocalRandom for lock-free, contention-free random number generation.
 * Each message has an independent probability of being sampled.
 *
 * Example usage:
 * - rate=0.1 logs approximately 10% of messages
 * - rate=0.01 logs approximately 1% of messages
 * - rate=1.0 logs all messages (no sampling)
 * - rate=0.0 logs no messages
 *
 * Thread-safe and suitable for high-concurrency Flink operators.
 */
public class FixedRateSampler implements Sampler {

    private static final long serialVersionUID = 1L;

    private final double rate;
    private final ConcurrentHashMap<String, SamplerState> states = new ConcurrentHashMap<>();

    /**
     * Per-key state for tracking suppression counts and last access time.
     */
    private static class SamplerState {
        final AtomicLong suppressedCount = new AtomicLong(0);
        volatile long lastAccessTimeMillis = System.currentTimeMillis();

        void touch() {
            lastAccessTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Create a fixed-rate sampler.
     *
     * @param rate sampling rate from 0.0 (no messages) to 1.0 (all messages)
     * @throws IllegalArgumentException if rate is not in range [0.0, 1.0]
     */
    public FixedRateSampler(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0, got: " + rate);
        }
        this.rate = rate;
    }

    @Override
    public boolean shouldSample(String key) {
        // Fail-safe - null key always samples:
        if (key == null) {
            return true;
        }

        // Edge cases - no random needed:
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }

        // ThreadLocalRandom is lock-free and has no contention:
        boolean sampled = ThreadLocalRandom.current().nextDouble() < rate;

        // Touch state when sampled-in to prevent cleanup of active keys:
        if (sampled) {
            states.computeIfAbsent(key, k -> new SamplerState()).touch();
        }

        return sampled;
    }

    @Override
    public void recordSuppressed(String key) {
        if (key == null) {
            return;
        }
        SamplerState state = states.computeIfAbsent(key, k -> new SamplerState());
        state.suppressedCount.incrementAndGet();
        state.touch();
    }

    @Override
    public long getSuppressedCount(String key) {
        if (key == null) {
            return 0;
        }
        SamplerState state = states.get(key);
        return state != null ? state.suppressedCount.get() : 0;
    }

    @Override
    public long getTotalSuppressedCount() {
        return states.values().stream()
                .mapToLong(state -> state.suppressedCount.get())
                .sum();
    }

    @Override
    public int getTrackedKeyCount() {
        return states.size();
    }

    @Override
    public String getSuppressedSummary(boolean resetAfter) {
        if (states.isEmpty()) {
            return "No messages sampled out";
        }

        String summary = states.entrySet().stream()
                .filter(e -> e.getValue().suppressedCount.get() > 0)
                .map(e -> e.getKey() + "=~" + e.getValue().suppressedCount.get() + " sampled out")
                .collect(Collectors.joining(", "));

        if (resetAfter) {
            reset();
        }

        return summary.isEmpty() ? "No messages sampled out" : summary;
    }

    @Override
    public void reset() {
        states.values().forEach(state -> state.suppressedCount.set(0));
    }

    /**
     * Get the configured sampling rate.
     *
     * @return sampling rate from 0.0 to 1.0
     */
    public double getRate() {
        return rate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Thread-safe: uses ConcurrentHashMap iterator which is weakly consistent.
     * Concurrent modifications during iteration are safe but may not be reflected
     * in the cleanup pass.
     */
    @Override
    public int cleanup(long maxAgeMillis) {
        if (maxAgeMillis <= 0) {
            return 0;
        }

        int removed = 0;
        long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

        Iterator<Map.Entry<String, SamplerState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SamplerState> entry = iterator.next();
            if (entry.getValue().lastAccessTimeMillis < cutoffTime) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }
}
