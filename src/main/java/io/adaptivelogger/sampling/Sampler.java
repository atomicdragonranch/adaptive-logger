package io.adaptivelogger.sampling;

import java.io.Serializable;

/**
 * Interface for log message sampling strategies.
 *
 * Sampling reduces log volume while maintaining statistical visibility into system behavior.
 * Unlike rate limiting (which prevents floods of identical messages), sampling provides
 * a representative subset of high-volume log streams.
 *
 * Implementations must be thread-safe for use in concurrent Flink operators and
 * serializable for Flink checkpoint compatibility.
 *
 * @see FixedRateSampler for percentage-based sampling
 * @see CountBasedSampler for every-Nth-message sampling
 */
public interface Sampler extends Serializable {

    /**
     * Determine if a message with the given key should be sampled (logged).
     *
     * @param key unique identifier for the message type (typically the format string)
     * @return true if message should be logged, false if it should be suppressed
     */
    boolean shouldSample(String key);

    /**
     * Record that a message was suppressed due to sampling.
     * Called when shouldSample() returns false to track suppression counts.
     *
     * @param key the message key that was suppressed
     */
    void recordSuppressed(String key);

    /**
     * Get the count of suppressed messages for a specific key.
     *
     * @param key the message key to query
     * @return number of messages suppressed for this key
     */
    long getSuppressedCount(String key);

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

    /**
     * Get a summary of suppressed messages suitable for logging.
     *
     * <p>Format varies by implementation:
     * <ul>
     *   <li>{@link FixedRateSampler}: Uses "~" prefix (e.g., "key=~10 sampled out") since
     *       random sampling produces approximate counts</li>
     *   <li>{@link CountBasedSampler}: Uses exact counts (e.g., "key=10 sampled out") since
     *       deterministic sampling produces exact counts</li>
     * </ul>
     *
     * @param resetAfter if true, reset suppression counts after generating summary
     * @return summary string suitable for logging
     */
    String getSuppressedSummary(boolean resetAfter);

    /**
     * Reset all suppression counts to zero.
     */
    void reset();

    /**
     * Remove entries that have not been accessed for longer than the specified age.
     * Call this periodically (e.g., on Flink checkpoint) to prevent unbounded memory growth
     * when using dynamic message keys.
     *
     * <p><b>Clock Drift Note:</b> This method uses {@code System.currentTimeMillis()} for
     * timestamp comparisons. In distributed Flink environments, clock skew between TaskManagers
     * may cause entries to be cleaned up earlier or later than expected. For most use cases,
     * this is acceptable since cleanup is a memory optimization, not a correctness requirement.
     *
     * @param maxAgeMillis maximum age in milliseconds for entries to keep; entries older than
     *                     this will be removed. Values <= 0 result in no cleanup.
     * @return number of entries removed
     */
    int cleanup(long maxAgeMillis);
}
