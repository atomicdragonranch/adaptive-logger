package io.adaptivelogger;

import io.adaptivelogger.ratelimit.RateLimitedLogger;
import io.adaptivelogger.ratelimit.RateLimiter;
import io.adaptivelogger.ratelimit.TimeBasedRateLimiter;

import org.slf4j.event.Level;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Manages rate limiting for an adaptive logger instance.
 * Encapsulates the rate limiter and provides factory methods for creating
 * rate-limited loggers, along with metrics and summary reporting.
 */
class RateLimitManager {

    private final RateLimiter rateLimiter;

    RateLimitManager() {
        this.rateLimiter = new TimeBasedRateLimiter();
    }

    /**
     * Create a rate-limited logger that suppresses messages within the specified time window.
     *
     * @param logger the parent logger for delegation
     * @param duration the time duration
     * @param unit the time unit
     * @return a RateLimitedLogger for fluent API usage
     */
    RateLimitedLogger atMostEvery(IAdaptiveLogger logger, long duration, TimeUnit unit) {
        return new RateLimitedLogger(logger, Duration.ofMillis(unit.toMillis(duration)), null);
    }

    /**
     * Create a rate-limited logger with a specific message key.
     *
     * @param logger the parent logger for delegation
     * @param duration the time duration
     * @param unit the time unit
     * @param messageKey key for grouping similar messages
     * @return a RateLimitedLogger for fluent API usage
     */
    RateLimitedLogger atMostEvery(IAdaptiveLogger logger, long duration, TimeUnit unit, String messageKey) {
        return new RateLimitedLogger(logger, Duration.ofMillis(unit.toMillis(duration)), messageKey);
    }

    /**
     * Check if a message should be logged considering rate limiting.
     *
     * @param level the log level
     * @param key the message key for rate limiting
     * @param window the time window
     * @param shouldLog whether the level is enabled
     * @return true if the message should be logged
     */
    boolean shouldLogWithRateLimit(Level level, String key, Duration window, boolean shouldLog) {
        return shouldLog && rateLimiter.shouldLog(key, window);
    }

    /**
     * Get the underlying rate limiter instance.
     *
     * @return the rate limiter
     */
    RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Get a periodic summary of rate-limited messages.
     *
     * @param resetCounts whether to reset counts after getting the summary
     * @return summary of rate limiting activity
     */
    String getSummary(boolean resetCounts) {
        return rateLimiter.getSuppressedSummary(resetCounts);
    }

    /**
     * Get the total count of all suppressed messages across all rate-limiting keys.
     *
     * @return total number of suppressed messages
     */
    long getTotalSuppressedCount() {
        return rateLimiter.getTotalSuppressedCount();
    }

    /**
     * Get the number of unique keys currently being tracked by the rate limiter.
     *
     * @return number of tracked keys
     */
    int getTrackedKeyCount() {
        return rateLimiter.getTrackedKeyCount();
    }
}
