package io.adaptivelogger;

import org.slf4j.event.Level;
import io.adaptivelogger.model.LoggingStatistics;
import io.adaptivelogger.ratelimit.RateLimitedLogger;
import io.adaptivelogger.ratelimit.RateLimiter;
import io.adaptivelogger.sampling.SampledLogger;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Interface for adaptive logging functionality that extends SLF4J Logger with additional capabilities.
 * This interface provides advanced features beyond standard logging including:
 * - Lazy evaluation of log arguments
 * - Dynamic log level adjustment
 * - Rate limiting
 * - Sampling
 * - Ring buffer for error context
 * - Statistics tracking
 *
 * The factory pattern (AdaptiveLoggerFactory.getLogger()) should be used to obtain instances
 * rather than convenience methods to maintain clear separation of concerns and make the
 * factory pattern explicit in the codebase.
 */
public interface IAdaptiveLogger extends Logger {

    // --- SLF4J Logger Methods (Standard logging) ---
    // These are inherited from Logger interface but listed here for clarity
    // void trace(String message);
    // void trace(String format, Object arg);
    // void trace(String format, Object arg1, Object arg2);
    // void trace(String format, Object... arguments);
    // void trace(String message, Throwable t);
    // (and similar for debug, info, warn, error)

    // --- Lazy Evaluation Methods ---
    /**
     * Logs a trace message with lazy evaluation of arguments.
     * Arguments are only evaluated if trace level is enabled.
     */
    void traceLazy(String format, Supplier<?>... argumentSuppliers);

    /**
     * Logs a debug message with lazy evaluation of arguments.
     * Arguments are only evaluated if debug level is enabled.
     */
    void debugLazy(String format, Supplier<?>... argumentSuppliers);

    /**
     * Logs an info message with lazy evaluation of arguments.
     * Arguments are only evaluated if info level is enabled.
     */
    void infoLazy(String format, Supplier<?>... argumentSuppliers);

    /**
     * Logs a warning message with lazy evaluation of arguments.
     * Arguments are only evaluated if warn level is enabled.
     */
    void warnLazy(String format, Supplier<?>... argumentSuppliers);

    /**
     * Logs an error message with lazy evaluation of arguments.
     * Arguments are only evaluated if error level is enabled.
     */
    void errorLazy(String format, Supplier<?>... argumentSuppliers);

    // --- Level Management ---
    /**
     * Sets the current log level.
     * This overrides the default level from configuration.
     */
    void setLevel(Level level);

    /**
     * Gets the current effective log level.
     */
    Level getLevel();

    /**
     * Resets the log level to the default from configuration.
     */
    void resetLevel();

    // --- Level Checking (Extended from SLF4J) ---
    // These override SLF4J methods to check against adaptive level
    boolean isTraceEnabled();
    boolean isDebugEnabled();
    boolean isInfoEnabled();
    boolean isWarnEnabled();
    boolean isErrorEnabled();

    // --- Buffer Operations ---
    /**
     * Dumps the ring buffer contents at the current log level.
     * Respects cooldown period to prevent log flooding.
     */
    void dumpBuffer();

    /**
     * Clears all events from the ring buffer.
     */
    void clearBuffer();

    /**
     * Gets the current number of events in the ring buffer.
     */
    int getBufferSize();

    // --- Statistics ---
    /**
     * Gets current logging statistics including message counts and buffer state.
     */
    LoggingStatistics getStatistics();

    // --- Rate Limiting ---
    /**
     * Creates a rate-limited logger that logs at most once per duration.
     * Uses the log message as the deduplication key.
     */
    RateLimitedLogger atMostEvery(long duration, TimeUnit unit);

    /**
     * Creates a rate-limited logger with a specific message key for deduplication.
     */
    RateLimitedLogger atMostEvery(long duration, TimeUnit unit, String messageKey);

    /**
     * Gets a summary of rate limiting activity.
     *
     * @param resetCounts Whether to reset suppression counts after retrieving
     */
    String getRateLimitingSummary(boolean resetCounts);

    /**
     * Gets the total number of messages suppressed by rate limiting.
     */
    long getRateLimitingTotalSuppressedCount();

    /**
     * Gets the number of unique message keys being tracked for rate limiting.
     */
    int getRateLimitingTrackedKeyCount();

    // --- Sampling ---
    /**
     * Creates a sampled logger that logs approximately the specified percentage of messages.
     * Uses random sampling with the log message as the key.
     */
    SampledLogger sample(double rate);

    /**
     * Creates a sampled logger with a specific message key for consistent sampling.
     */
    SampledLogger sample(double rate, String messageKey);

    /**
     * Creates a sampled logger that logs every Nth occurrence.
     */
    SampledLogger sampleEvery(int n);

    /**
     * Creates a sampled logger that logs every Nth occurrence with a specific key.
     */
    SampledLogger sampleEvery(int n, String messageKey);

    /**
     * Gets the total number of messages suppressed by sampling.
     */
    long getSamplingTotalSuppressedCount();

    /**
     * Gets the number of unique message keys being tracked for sampling.
     */
    int getSamplingTrackedKeyCount();

    /**
     * Gets a summary of sampling activity.
     *
     * @param resetCounts Whether to reset suppression counts after retrieving
     */
    String getSamplingSummary(boolean resetCounts);

    /**
     * Cleans up expired samplers.
     *
     * @param maxAgeMillis Maximum age in milliseconds for inactive samplers
     * @return Number of samplers removed
     */
    int cleanupSamplers(long maxAgeMillis);

    /**
     * Clears all sampler caches immediately.
     *
     * @return Number of samplers cleared
     */
    int clearSamplerCaches();

    /**
     * Gets the current number of cached samplers.
     */
    int getCachedSamplerCount();

    // --- Advanced Methods ---
    /**
     * Checks if a message at the given level should be logged.
     * Takes into account current level and adaptive logging state.
     */
    boolean shouldLog(Level level);

    /**
     * Checks if a message should be logged with rate limiting applied.
     */
    boolean shouldLogWithRateLimit(Level level, String key, Duration window);

    /**
     * Gets the underlying rate limiter instance.
     */
    RateLimiter getRateLimiter();

    /**
     * Logs a message at the specified level.
     */
    void logAtLevel(Level level, String message);

    /**
     * Logs a formatted message at the specified level.
     */
    void logAtLevel(Level level, String format, Object... arguments);

    /**
     * Logs a message with throwable at the specified level.
     */
    void logAtLevel(Level level, String message, Throwable throwable);
}