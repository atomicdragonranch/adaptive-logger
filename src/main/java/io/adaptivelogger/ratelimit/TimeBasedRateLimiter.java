package io.adaptivelogger.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Time-based rate limiter implementation that allows one message per key per time window.
 *
 * <p>This implementation uses a simple time-window approach where each key can log
 * at most once per configured duration. Suppressed messages are counted for reporting.
 *
 * <p>Thread-safe for use in concurrent environments like Flink operators.
 *
 * <p><b>Clock Drift Consideration:</b> In distributed environments (e.g., multiple Flink
 * TaskManagers), each node uses its local system clock. Clock drift between nodes may cause
 * rate limiting windows to behave slightly differently across nodes. For most logging use
 * cases, this is acceptable. If strict global rate limiting is required, consider using a
 * centralized time source or accepting that each node rate-limits independently.
 *
 * <p>Example behavior:
 * <pre>
 * Time 0:00 - Log allowed for key "error-123"
 * Time 0:15 - Log suppressed for key "error-123" (within 30s window)
 * Time 0:31 - Log allowed again for key "error-123" (outside window)
 * </pre>
 */
public class TimeBasedRateLimiter implements RateLimiter {
    private static final long serialVersionUID = 1L;

    private final Clock clock;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    /**
     * State for each rate-limited key.
     */
    private static class WindowState {
        final AtomicReference<Instant> lastLogTime = new AtomicReference<>(Instant.MIN);
        final AtomicLong suppressedCount = new AtomicLong(0);
    }

    /**
     * Create a rate limiter with the system clock.
     */
    public TimeBasedRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * Create a rate limiter with a specific clock (useful for testing).
     *
     * @param clock the clock to use for timestamps
     */
    public TimeBasedRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean shouldLog(String key, Duration window) {
        if (key == null || window == null || window.isNegative() || window.isZero()) {
            // Invalid parameters, allow logging:
            return true;
        }

        WindowState state = windows.computeIfAbsent(key, k -> new WindowState());
        Instant now = clock.instant();

        // Use CAS (Compare-And-Swap) loop for lock-free thread safety:
        // CAS atomically reads current value, compares with expected, and swaps if match.
        // If another thread modifies the value between our read and swap, CAS fails and we retry.
        // This is faster than locks since threads never block - they just retry on contention.
        while (true) {
            Instant lastLog = state.lastLogTime.get();

            // Check if we're outside the rate limit window:
            if (Duration.between(lastLog, now).compareTo(window) >= 0) {
                // Attempt atomic update - if another thread beat us, retry the check:
                if (state.lastLogTime.compareAndSet(lastLog, now)) {
                    return true;
                }
                // Another thread updated; loop to re-check with new value:
            } else {
                // Within window, suppress:
                return false;
            }
        }
    }

    @Override
    public void recordSuppressed(String key) {
        WindowState state = windows.get(key);
        if (state != null) {
            state.suppressedCount.incrementAndGet();
        }
    }

    @Override
    public long getSuppressedCount(String key) {
        WindowState state = windows.get(key);
        return state != null ? state.suppressedCount.get() : 0;
    }

    @Override
    public void reset() {
        windows.clear();
    }

    @Override
    public String getSuppressedSummary(boolean resetAfter) {
        if (windows.isEmpty()) {
            return "No messages suppressed";
        }

        StringBuilder summary = new StringBuilder("Rate limiting summary: ");
        boolean first = true;

        for (var entry : windows.entrySet()) {
            String key = entry.getKey();
            WindowState state = entry.getValue();
            long count = state.suppressedCount.get();

            if (count > 0) {
                if (!first) {
                    summary.append(", ");
                }
                summary.append(key).append("=").append(count).append(" suppressed");
                first = false;

                if (resetAfter) {
                    state.suppressedCount.set(0);
                }
            }
        }

        return first ? "No messages suppressed" : summary.toString();
    }

    /**
     * Clean up old entries that haven't been used recently.
     * This prevents memory leaks for dynamic keys.
     *
     * @param maxAge remove entries older than this duration
     */
    public void cleanupOldEntries(Duration maxAge) {
        Instant cutoff = clock.instant().minus(maxAge);
        windows.entrySet().removeIf(entry -> {
            Instant lastLog = entry.getValue().lastLogTime.get();
            return lastLog.isBefore(cutoff);
        });
    }

    /**
     * Get the total count of all suppressed messages across all keys.
     * Useful for metrics and monitoring.
     *
     * @return total number of suppressed messages
     */
    public long getTotalSuppressedCount() {
        return windows.values().stream()
                .mapToLong(state -> state.suppressedCount.get())
                .sum();
    }

    /**
     * Get the number of unique keys currently being tracked.
     * Useful for monitoring memory usage.
     *
     * @return number of tracked keys
     */
    public int getTrackedKeyCount() {
        return windows.size();
    }
}
