package io.adaptivelogger.ratelimit;

import java.io.Serializable;
import java.time.Duration;

/**
 * Interface for rate limiting log messages to prevent flooding.
 *
 * <p>Rate limiting helps prevent log spam during failure scenarios while
 * maintaining visibility into issues. Messages that are rate-limited are
 * not lost - they're counted and periodically summarized.
 *
 * <p>Example usage:
 * <pre>{@code
 * if (rateLimiter.shouldLog("connection-failed", Duration.ofSeconds(30))) {
 *     LOG.warn("Connection failed to {}", endpoint);
 * } else {
 *     rateLimiter.recordSuppressed("connection-failed");
 * }
 * }</pre>
 *
 * @see TimeBasedRateLimiter for the default implementation
 */
public interface RateLimiter extends Serializable {

    /**
     * Check if a message with the given key should be logged.
     *
     * @param key unique identifier for the message type
     * @param window time window for rate limiting
     * @return true if the message should be logged, false if it should be suppressed
     */
    boolean shouldLog(String key, Duration window);

    /**
     * Record that a message was suppressed due to rate limiting.
     * This allows tracking of how many messages were dropped.
     *
     * @param key unique identifier for the message type
     */
    void recordSuppressed(String key);

    /**
     * Get the number of suppressed messages for a given key.
     *
     * @param key unique identifier for the message type
     * @return number of messages suppressed
     */
    long getSuppressedCount(String key);

    /**
     * Reset all rate limiting state. Useful for testing or
     * when changing configuration.
     */
    void reset();

    /**
     * Get a summary of all suppressed messages for logging.
     * This is typically called periodically to report on rate limiting activity.
     *
     * @param resetAfter if true, reset counts after getting summary
     * @return summary string suitable for logging
     */
    String getSuppressedSummary(boolean resetAfter);

    /**
     * Get the total count of all suppressed messages across all keys.
     * Useful for metrics and monitoring.
     *
     * @return total number of suppressed messages
     */
    long getTotalSuppressedCount();

    /**
     * Get the number of unique keys currently being tracked.
     * Useful for monitoring memory usage.
     *
     * @return number of tracked keys
     */
    int getTrackedKeyCount();
}
