package io.adaptivelogger.sampling;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Count-based sampler that logs every Nth message.
 *
 * Unlike FixedRateSampler (which uses random sampling), CountBasedSampler is deterministic.
 * The first message is always logged, then every Nth message thereafter.
 *
 * Example usage:
 * - n=10 logs message 1, 11, 21, 31... (every 10th)
 * - n=100 logs message 1, 101, 201... (every 100th)
 * - n=1 logs all messages (no sampling)
 *
 * Thread-safe using AtomicLong for concurrent message counting.
 */
public class CountBasedSampler implements Sampler {

    private static final long serialVersionUID = 1L;

    private final int n;
    private final ConcurrentHashMap<String, SamplerState> states = new ConcurrentHashMap<>();

    /**
     * Per-key state for tracking message counts, suppression, and last access time.
     */
    private static class SamplerState {
        final AtomicLong messageCount = new AtomicLong(0);
        final AtomicLong suppressedCount = new AtomicLong(0);
        volatile long lastAccessTimeMillis = System.currentTimeMillis();

        void touch() {
            lastAccessTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Create a count-based sampler.
     *
     * @param n log every Nth message (n=10 means log 1st, 11th, 21st, etc.)
     * @throws IllegalArgumentException if n is less than 1
     */
    public CountBasedSampler(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Sample interval must be at least 1, got: " + n);
        }
        this.n = n;
    }

    @Override
    public boolean shouldSample(String key) {
        // n=1 means log everything:
        if (n <= 1) {
            return true;
        }

        // Fail-safe - null key always samples:
        if (key == null) {
            return true;
        }

        SamplerState state = states.computeIfAbsent(key, k -> new SamplerState());
        long count = state.messageCount.incrementAndGet();
        state.touch();

        // Log when count % n == 1 (first message and every Nth after):
        // count=1: 1 % 10 = 1 -> log
        // count=2: 2 % 10 = 2 -> suppress
        // count=11: 11 % 10 = 1 -> log
        return count % n == 1;
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
                .map(e -> e.getKey() + "=" + e.getValue().suppressedCount.get() + " sampled out")
                .collect(Collectors.joining(", "));

        if (resetAfter) {
            reset();
        }

        return summary.isEmpty() ? "No messages sampled out" : summary;
    }

    @Override
    public void reset() {
        states.values().forEach(state -> {
            state.messageCount.set(0);
            state.suppressedCount.set(0);
        });
    }

    /**
     * Get the configured sample interval.
     *
     * @return sample interval N (log every Nth message)
     */
    public int getInterval() {
        return n;
    }

    /**
     * Get the current message count for a specific key.
     * Useful for debugging and testing.
     *
     * @param key the message key to query
     * @return current message count, or 0 if key not tracked
     */
    public long getMessageCount(String key) {
        if (key == null) {
            return 0;
        }
        SamplerState state = states.get(key);
        return state != null ? state.messageCount.get() : 0;
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
