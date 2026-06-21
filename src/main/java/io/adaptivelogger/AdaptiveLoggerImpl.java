package io.adaptivelogger;

import io.adaptivelogger.mdc.MDCProvider;
import io.adaptivelogger.model.LogEvent;
import io.adaptivelogger.model.LoggingStatistics;
import io.adaptivelogger.model.MDContext;
import io.adaptivelogger.ratelimit.RateLimitedLogger;
import io.adaptivelogger.ratelimit.RateLimiter;
import io.adaptivelogger.sampling.SampledLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Implementation of IAdaptiveLogger that wraps standard SLF4J loggers to provide dynamic log level management,
 * error pattern detection, and debug context buffering.
 *
 * <p><b>Important Architecture Principle:</b> Log4j configuration sets the ceiling,
 * AdaptiveLogger controls the floor. This means:
 * <ul>
 *   <li>Log4j must be configured at DEBUG or TRACE level to allow those messages through</li>
 *   <li>AdaptiveLogger then dynamically controls which messages are actually logged</li>
 *   <li>If Log4j is set to INFO, AdaptiveLogger cannot log DEBUG/TRACE even during escalation</li>
 * </ul>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Dynamic log level changes without restart</li>
 *   <li>Automatic escalation to DEBUG/TRACE when error threshold exceeded</li>
 *   <li>Circular buffer stores recent DEBUG/TRACE events for post-mortem analysis</li>
 *   <li>MDC context preservation for distributed tracing in Flink</li>
 *   <li>Lazy evaluation methods for performance-critical paths</li>
 *   <li>Automatic de-escalation after configurable duration</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * private static final IAdaptiveLogger LOG = AdaptiveLoggerFactory.getLogger(MyClass.class);
 * </pre>
 *
 * @see AdaptiveLoggerFactory for obtaining instances
 * @see AdaptiveLoggingConfig for configuration options
 * @see IAdaptiveLogger for the interface contract
 */
public class AdaptiveLoggerImpl implements IAdaptiveLogger {
    private final Logger delegate;
    private final String name;
    private final AdaptiveLoggingConfig config;
    private final MDCProvider mdcProvider;

    // Delegates:
    private final LogLevelController levelController;
    private final RingBufferManager ringBufferManager;
    private final RateLimitManager rateLimitManager;
    private final SamplingManager samplingManager;

    // Performance counters:
    private final AtomicInteger logCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    public AdaptiveLoggerImpl(String name, AdaptiveLoggingConfig config) {
        this.name = name;
        this.delegate = LoggerFactory.getLogger(name);
        this.config = config;
        this.mdcProvider = new MDCProvider.DefaultMDCProvider();

        this.levelController = new LogLevelController(config);
        this.ringBufferManager = new RingBufferManager(config);
        this.rateLimitManager = new RateLimitManager();
        this.samplingManager = new SamplingManager();
    }

    // --- Trace Level ---
    /**
     * TRACE is the most verbose logging level with significant performance impact.
     * In production environments, TRACE can:
     * <ul>
     *   <li>Generate 10,000+ log lines per second per TaskManager</li>
     *   <li>Reduce throughput by ~20%</li>
     *   <li>Increase CPU usage by ~25%</li>
     *   <li>Cause excessive GC pressure</li>
     * </ul>
     *
     * <p>Use TRACE only for temporary, targeted debugging. Always use lazy evaluation
     * for TRACE logs in performance-critical paths.
     *
     * <p><b>Configuration:</b> Requires both:
     * <ol>
     *   <li>Log4j configured with logger.stronachgroup.level = TRACE</li>
     *   <li>AdaptiveLogger level set to TRACE (via config or escalation)</li>
     * </ol>
     *
     * @see #traceLazy for deferred evaluation in hot paths
     */
    public void trace(String message) {
        trace(message, (Object[]) null);
    }

    public void trace(String format, Object arg) {
        trace(format, new Object[]{arg});
    }

    public void trace(String format, Object arg1, Object arg2) {
        trace(format, new Object[]{arg1, arg2});
    }

    public void trace(String format, Object... arguments) {
        LogEvent event = createLogEvent(Level.TRACE, format, arguments, null);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(format, arguments));
        }
    }

    public void trace(String message, Throwable t) {
        LogEvent event = createLogEvent(Level.TRACE, message, null, t);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(message, t));
        }
    }

    /**
     * Lazy trace logging with deferred argument evaluation.
     * Arguments are stored as Suppliers and only evaluated when:
     * - TRACE level is currently enabled, OR
     * - Buffer is dumped after escalation
     *
     * Use this for the most verbose logging in performance-critical paths
     * to avoid evaluating expensive arguments during normal operation.
     *
     * MDC Context Behavior:
     * The MDC context is captured at the time this method is called, NOT when
     * suppliers are evaluated. This ensures buffered events preserve the correct
     * contextual information from when the log was created (e.g., market ID,
     * transaction ID). If you need dynamic MDC values, include them in your
     * supplier:
     *   LOG.traceLazy("Context: {}", () -> MDC.get("marketId"));
     *
     * Example:
     * LOG.traceLazy("Detailed state: {}", () -> buildDetailedState());
     *
     * @param format Message format with {} placeholders
     * @param argumentSuppliers Suppliers for lazy argument evaluation
     */
    @SafeVarargs
    public final void traceLazy(String format, Supplier<?>... argumentSuppliers) {
        // Create lazy event (no evaluation):
        LogEvent event = createLazyLogEvent(Level.TRACE, format, argumentSuppliers, null);
        ringBufferManager.bufferEvent(event);

        // Only evaluate if currently logging (uses cached evaluation):
        if (shouldLog(Level.TRACE)) {
            Object[] args = event.getEvaluatedArguments();
            withMDC(() -> delegate.trace(format, args));
        }
    }

    // --- Debug Level ---
    public void debug(String message) {
        debug(message, (Object[]) null);
    }

    public void debug(String format, Object arg) {
        debug(format, new Object[]{arg});
    }

    public void debug(String format, Object arg1, Object arg2) {
        debug(format, new Object[]{arg1, arg2});
    }

    public void debug(String format, Object... arguments) {
        LogEvent event = createLogEvent(Level.DEBUG, format, arguments, null);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(format, arguments));
        }
    }

    public void debug(String message, Throwable t) {
        LogEvent event = createLogEvent(Level.DEBUG, message, null, t);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(message, t));
        }
    }

    /**
     * Lazy debug logging with deferred argument evaluation.
     * Arguments are stored as Suppliers and only evaluated when:
     * - DEBUG level is currently enabled, OR
     * - Buffer is dumped after escalation
     *
     * Use this in performance-critical paths (e.g., serializers) to avoid
     * evaluating expensive arguments during normal operation.
     *
     * MDC Context Behavior:
     * The MDC context is captured at the time this method is called, NOT when
     * suppliers are evaluated. This ensures buffered events preserve the correct
     * contextual information from when the log was created (e.g., market ID,
     * transaction ID). If you need dynamic MDC values, include them in your
     * supplier:
     *   LOG.debugLazy("Context: {}", () -> MDC.get("marketId"));
     *
     * Example:
     * LOG.debugLazy("Serializing size: {} bytes", () -> bytes.length);
     *
     * @param format Message format with {} placeholders
     * @param argumentSuppliers Suppliers for lazy argument evaluation
     */
    @SafeVarargs
    public final void debugLazy(String format, Supplier<?>... argumentSuppliers) {
        // Create lazy event (no evaluation):
        LogEvent event = createLazyLogEvent(Level.DEBUG, format, argumentSuppliers, null);
        ringBufferManager.bufferEvent(event);

        // Only evaluate if currently logging (uses cached evaluation):
        if (shouldLog(Level.DEBUG)) {
            Object[] args = event.getEvaluatedArguments();
            withMDC(() -> delegate.debug(format, args));
        }
    }

    // --- Info Level ---
    public void info(String message) {
        info(message, (Object[]) null);
    }

    public void info(String format, Object arg) {
        info(format, new Object[]{arg});
    }

    public void info(String format, Object arg1, Object arg2) {
        info(format, new Object[]{arg1, arg2});
    }

    public void info(String format, Object... arguments) {
        LogEvent event = createLogEvent(Level.INFO, format, arguments, null);

        if (config.isBufferInfoMessages()) {
            ringBufferManager.bufferEvent(event);
        }

        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(format, arguments));
        }
    }

    public void info(String message, Throwable t) {
        LogEvent event = createLogEvent(Level.INFO, message, null, t);

        if (config.isBufferInfoMessages()) {
            ringBufferManager.bufferEvent(event);
        }

        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(message, t));
        }
    }

    /**
     * Lazy info logging with deferred argument evaluation.
     *
     * MDC Context Behavior:
     * The MDC context is captured at event creation time, preserving the
     * correct contextual information from when the log was created:
     *
     * @param format Message format with {} placeholders
     * @param argumentSuppliers Suppliers for lazy argument evaluation
     */
    @SafeVarargs
    public final void infoLazy(String format, Supplier<?>... argumentSuppliers) {
        LogEvent event = createLazyLogEvent(Level.INFO, format, argumentSuppliers, null);

        if (config.isBufferInfoMessages()) {
            ringBufferManager.bufferEvent(event);
        }

        if (shouldLog(Level.INFO)) {
            Object[] args = event.getEvaluatedArguments();
            withMDC(() -> delegate.info(format, args));
        }
    }

    // --- Warn Level ---
    public void warn(String message) {
        warn(message, (Object[]) null);
    }

    public void warn(String format, Object arg) {
        warn(format, new Object[]{arg});
    }

    public void warn(String format, Object arg1, Object arg2) {
        warn(format, new Object[]{arg1, arg2});
    }

    public void warn(String format, Object... arguments) {
        LogEvent event = createLogEvent(Level.WARN, format, arguments, null);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(format, arguments));
        }
    }

    public void warn(String message, Throwable t) {
        LogEvent event = createLogEvent(Level.WARN, message, null, t);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(message, t));
        }
    }

    /**
     * Lazy warn logging with deferred argument evaluation.
     *
     * MDC Context Behavior:
     * The MDC context is captured at event creation time, preserving the
     * correct contextual information from when the log was created:
     *
     * @param format Message format with {} placeholders
     * @param argumentSuppliers Suppliers for lazy argument evaluation
     */
    @SafeVarargs
    public final void warnLazy(String format, Supplier<?>... argumentSuppliers) {
        LogEvent event = createLazyLogEvent(Level.WARN, format, argumentSuppliers, null);
        ringBufferManager.bufferEvent(event);

        if (shouldLog(Level.WARN)) {
            Object[] args = event.getEvaluatedArguments();
            withMDC(() -> delegate.warn(format, args));
        }
    }

    // --- Error Level ---
    public void error(String message) {
        error(message, (Object[]) null);
    }

    public void error(String format, Object arg) {
        error(format, new Object[]{arg});
    }

    public void error(String format, Object arg1, Object arg2) {
        error(format, new Object[]{arg1, arg2});
    }

    public void error(String format, Object... arguments) {
        handleError(() -> createLogEvent(Level.ERROR, format, arguments, null));
        withMDC(() -> delegate.error(format, arguments));
    }

    public void error(String message, Throwable t) {
        handleError(() -> createLogEvent(Level.ERROR, message, null, t));
        withMDC(() -> delegate.error(message, t));
    }

    /**
     * Lazy error logging with deferred argument evaluation.
     * Note: Error level always evaluates suppliers immediately since errors
     * are always logged. Suppliers are evaluated once and reused for both
     * buffering and immediate logging.
     *
     * MDC Context Behavior:
     * The MDC context is captured at event creation time, preserving the
     * correct contextual information from when the log was created:
     *
     * @param format Message format with {} placeholders
     * @param argumentSuppliers Suppliers for lazy argument evaluation
     */
    @SafeVarargs
    public final void errorLazy(String format, Supplier<?>... argumentSuppliers) {
        // Create lazy event and evaluate once (errors are always logged immediately):
        LogEvent event = createLazyLogEvent(Level.ERROR, format, argumentSuppliers, null);
        Object[] args = event.getEvaluatedArguments();
        handleError(() -> event);
        withMDC(() -> delegate.error(format, args));
    }

    // --- Dynamic Level Management ---
    public void setLevel(Level level) {
        levelController.setLevel(level, delegate, name);
    }

    public Level getLevel() {
        return levelController.getLevel();
    }

    public void resetLevel() {
        levelController.resetLevel(delegate, name);
    }

    // --- Level Checks (SLF4J-compatible API) ---
    /**
     * Checks if TRACE level is enabled for this logger.
     * Use this to avoid expensive computation of log arguments when trace is disabled.
     *
     * Example:
     * if (logger.isTraceEnabled()) {
     *     logger.trace("Expensive: {}", computeExpensiveString());
     * }
     *
     * @return true if TRACE level would be logged
     */
    public boolean isTraceEnabled() {
        return shouldLog(Level.TRACE);
    }

    /**
     * Checks if DEBUG level is enabled for this logger.
     * Use this to avoid expensive computation of log arguments when debug is disabled.
     *
     * Example:
     * if (logger.isDebugEnabled()) {
     *     logger.debug("Expensive: {}", computeExpensiveString());
     * }
     *
     * @return true if DEBUG level would be logged
     */
    public boolean isDebugEnabled() {
        return shouldLog(Level.DEBUG);
    }

    /**
     * Checks if INFO level is enabled for this logger.
     *
     * @return true if INFO level would be logged
     */
    public boolean isInfoEnabled() {
        return shouldLog(Level.INFO);
    }

    /**
     * Checks if WARN level is enabled for this logger.
     *
     * @return true if WARN level would be logged
     */
    public boolean isWarnEnabled() {
        return shouldLog(Level.WARN);
    }

    /**
     * Checks if ERROR level is enabled for this logger.
     *
     * @return true if ERROR level would be logged
     */
    public boolean isErrorEnabled() {
        return shouldLog(Level.ERROR);
    }

    // --- Buffer Management ---
    public void dumpBuffer() {
        ringBufferManager.dumpBuffer(delegate, levelController.getErrorDetector());
    }

    public void clearBuffer() {
        ringBufferManager.clearBuffer();
    }

    public int getBufferSize() {
        return ringBufferManager.getBufferSize();
    }

    // --- Statistics ---
    public LoggingStatistics getStatistics() {
        return LoggingStatistics.builder()
            .loggerName(name)
            .currentLevel(levelController.getLevel())
            .totalLogs(logCount.get())
            .errorCount(errorCount.get())
            .bufferSize(ringBufferManager.getBufferSize())
            .bufferCapacity(ringBufferManager.getBufferCapacity())
            .errorDetectorState(levelController.getErrorDetector().getState())
            .build();
    }

    // --- Package-Private Methods ---

    /**
     * Check if a message at the given level should be logged.
     * Package-private for use by RateLimitedLogger and SampledLogger.
     *
     * When adaptive logging is disabled, delegates to the underlying SLF4J logger.
     *
     * @param level the log level to check
     * @return true if the level is enabled for logging
     */
    public boolean shouldLog(Level level) {
        return levelController.shouldLog(level, delegate);
    }

    // --- Rate Limiting ---
    /**
     * Create a rate-limited logger that suppresses messages within the specified time window.
     *
     * <p>Example usage:
     * <pre>{@code
     * // Log at most once every 30 seconds
     * LOG.atMostEvery(30, TimeUnit.SECONDS)
     *    .warn("Connection pool exhausted");
     *
     * // Include count of suppressed messages
     * LOG.atMostEvery(1, TimeUnit.MINUTES)
     *    .withSuppressedCount()
     *    .error("Critical failure", exception);
     * }</pre>
     *
     * @param duration the time duration
     * @param unit the time unit
     * @return a RateLimitedLogger for fluent API usage
     */
    public RateLimitedLogger atMostEvery(long duration, TimeUnit unit) {
        return rateLimitManager.atMostEvery(this, duration, unit);
    }

    /**
     * Create a rate-limited logger with a specific message key.
     *
     * @param duration the time duration
     * @param unit the time unit
     * @param messageKey key for grouping similar messages
     * @return a RateLimitedLogger for fluent API usage
     */
    public RateLimitedLogger atMostEvery(long duration, TimeUnit unit, String messageKey) {
        return rateLimitManager.atMostEvery(this, duration, unit, messageKey);
    }

    // --- Sampling ---

    /**
     * Create a sampled logger that logs a percentage of messages.
     *
     * <p>Samplers are cached by configuration to avoid GC pressure in high-throughput scenarios.
     * Multiple calls with the same rate share the same underlying sampler.
     *
     * <p>Example usage:
     * <pre>{@code
     * // Log ~10% of messages:
     * LOG.sample(0.1)
     *    .debug("High-volume event: {}", eventId);
     *
     * // Log ~1% of messages:
     * LOG.sample(0.01)
     *    .trace("Market tick: {}", tick);
     * }</pre>
     *
     * @param rate sampling rate from 0.0 (no messages) to 1.0 (all messages)
     * @return a SampledLogger for fluent API usage
     */
    public SampledLogger sample(double rate) {
        return samplingManager.sample(this, rate);
    }

    /**
     * Create a sampled logger with a specific message key.
     *
     * @param rate sampling rate from 0.0 to 1.0
     * @param messageKey key for grouping similar messages
     * @return a SampledLogger for fluent API usage
     */
    public SampledLogger sample(double rate, String messageKey) {
        return samplingManager.sample(this, rate, messageKey);
    }

    /**
     * Create a sampled logger that logs every Nth message.
     *
     * <p>Samplers are cached by configuration to avoid GC pressure in high-throughput scenarios.
     * Multiple calls with the same interval share the same underlying sampler.
     *
     * <p>Example usage:
     * <pre>{@code
     * // Log every 100th message:
     * LOG.sampleEvery(100)
     *    .trace("High-volume data: {}", data);
     *
     * // Log every 10th bet:
     * LOG.sampleEvery(10)
     *    .debug("Processing bet: {}", betId);
     * }</pre>
     *
     * @param n log every Nth message (first message always logged)
     * @return a SampledLogger for fluent API usage
     */
    public SampledLogger sampleEvery(int n) {
        return samplingManager.sampleEvery(this, n);
    }

    /**
     * Create a sampled logger that logs every Nth message with a specific key.
     *
     * @param n log every Nth message
     * @param messageKey key for grouping similar messages
     * @return a SampledLogger for fluent API usage
     */
    public SampledLogger sampleEvery(int n, String messageKey) {
        return samplingManager.sampleEvery(this, n, messageKey);
    }

    /**
     * Check if a message should be logged considering rate limiting.
     * Internal method used by RateLimitedLogger.
     *
     * @param level the log level
     * @param key the message key for rate limiting
     * @param window the time window
     * @return true if the message should be logged
     */
    public boolean shouldLogWithRateLimit(Level level, String key, Duration window) {
        return rateLimitManager.shouldLogWithRateLimit(level, key, window, shouldLog(level));
    }

    /**
     * Get the rate limiter for this logger.
     * Internal method used by RateLimitedLogger.
     *
     * @return the rate limiter instance
     */
    public RateLimiter getRateLimiter() {
        return rateLimitManager.getRateLimiter();
    }

    /**
     * Log at a specific level. Internal method used by RateLimitedLogger.
     *
     * @param level the log level
     * @param message the message
     */
    public void logAtLevel(Level level, String message) {
        switch (level) {
            case TRACE:
                trace(message);
                break;
            case DEBUG:
                debug(message);
                break;
            case INFO:
                info(message);
                break;
            case WARN:
                warn(message);
                break;
            case ERROR:
                error(message);
                break;
        }
    }

    /**
     * Log at a specific level with arguments. Internal method used by RateLimitedLogger.
     *
     * @param level the log level
     * @param format the message format
     * @param arguments the format arguments
     */
    public void logAtLevel(Level level, String format, Object... arguments) {
        switch (level) {
            case TRACE:
                trace(format, arguments);
                break;
            case DEBUG:
                debug(format, arguments);
                break;
            case INFO:
                info(format, arguments);
                break;
            case WARN:
                warn(format, arguments);
                break;
            case ERROR:
                error(format, arguments);
                break;
        }
    }

    /**
     * Log at a specific level with throwable. Internal method used by RateLimitedLogger.
     *
     * @param level the log level
     * @param message the message
     * @param throwable the exception
     */
    public void logAtLevel(Level level, String message, Throwable throwable) {
        switch (level) {
            case TRACE:
                trace(message, throwable);
                break;
            case DEBUG:
                debug(message, throwable);
                break;
            case INFO:
                info(message, throwable);
                break;
            case WARN:
                warn(message, throwable);
                break;
            case ERROR:
                error(message, throwable);
                break;
        }
    }

    /**
     * Get a periodic summary of rate-limited messages.
     * This should be called periodically (e.g., every minute) to report on suppressed messages.
     *
     * @param resetCounts whether to reset counts after getting the summary
     * @return summary of rate limiting activity
     */
    public String getRateLimitingSummary(boolean resetCounts) {
        return rateLimitManager.getSummary(resetCounts);
    }

    /**
     * Get the total count of all suppressed messages across all rate-limiting keys.
     * Useful for metrics integration (e.g., Datadog, Prometheus, Flink Metrics).
     *
     * @return total number of suppressed messages
     */
    public long getRateLimitingTotalSuppressedCount() {
        return rateLimitManager.getTotalSuppressedCount();
    }

    /**
     * Get the number of unique keys currently being tracked by the rate limiter.
     * Useful for monitoring memory usage of the rate limiting system.
     *
     * @return number of tracked keys
     */
    public int getRateLimitingTrackedKeyCount() {
        return rateLimitManager.getTrackedKeyCount();
    }

    // --- Sampling Metrics ---

    /**
     * Get the total count of all sampled-out messages across all samplers.
     * Useful for metrics integration (e.g., Datadog, Prometheus, Flink Metrics).
     *
     * @return total number of sampled-out messages
     */
    public long getSamplingTotalSuppressedCount() {
        return samplingManager.getTotalSuppressedCount();
    }

    /**
     * Get the total number of unique keys being tracked across all samplers.
     * Useful for monitoring memory usage of the sampling system.
     *
     * @return total number of tracked keys
     */
    public int getSamplingTrackedKeyCount() {
        return samplingManager.getTrackedKeyCount();
    }

    /**
     * Get a summary of sampling activity across all samplers.
     *
     * @param resetCounts whether to reset counts after getting the summary
     * @return summary of sampling activity
     */
    public String getSamplingSummary(boolean resetCounts) {
        return samplingManager.getSummary(resetCounts);
    }

    /**
     * Cleanup stale entries from all cached samplers.
     * Call this periodically (e.g., on Flink checkpoint) to prevent memory growth.
     *
     * <p>Example usage in a Flink operator:
     * <pre>{@code
     * @Override
     * public void snapshotState(FunctionSnapshotContext context) {
     *     LOG.cleanupSamplers(300_000); // Remove entries older than 5 minutes:
     * }
     * }</pre>
     *
     * @param maxAgeMillis entries older than this will be removed
     * @return total number of entries removed
     */
    public int cleanupSamplers(long maxAgeMillis) {
        return samplingManager.cleanupSamplers(maxAgeMillis);
    }

    /**
     * Clear all cached sampler instances.
     * Use this to reclaim memory when sampler configurations are no longer needed.
     *
     * <p>This clears the sampler instances themselves, not just their internal state.
     * After calling this method, new sampler instances will be created on subsequent
     * {@code sample()} or {@code sampleEvery()} calls.
     *
     * <p>Typically useful when:
     * <ul>
     *   <li>Dynamically-generated sampling rates are no longer needed</li>
     *   <li>Resetting state during testing</li>
     *   <li>Reclaiming memory in long-running applications</li>
     * </ul>
     *
     * @return total number of cached samplers that were cleared
     */
    public int clearSamplerCaches() {
        return samplingManager.clearSamplerCaches();
    }

    /**
     * Get the number of cached sampler instances.
     * Useful for monitoring memory usage of the sampling system.
     *
     * @return total number of cached samplers (fixed-rate + count-based)
     */
    public int getCachedSamplerCount() {
        return samplingManager.getCachedSamplerCount();
    }

    // --- SLF4J Delegation Methods ---

    @Override
    public String getName() {
        return name;
    }

    // --- Marker-based methods (delegated to underlying logger) ---

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(marker, msg));
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(marker, format, arg));
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(marker, format, arg1, arg2));
        }
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(marker, format, argArray));
        }
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        if (shouldLog(Level.TRACE)) {
            withMDC(() -> delegate.trace(marker, msg, t));
        }
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(marker, msg));
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(marker, format, arg));
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(marker, format, arg1, arg2));
        }
    }

    @Override
    public void debug(Marker marker, String format, Object... argArray) {
        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(marker, format, argArray));
        }
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        if (shouldLog(Level.DEBUG)) {
            withMDC(() -> delegate.debug(marker, msg, t));
        }
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(marker, msg));
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(marker, format, arg));
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(marker, format, arg1, arg2));
        }
    }

    @Override
    public void info(Marker marker, String format, Object... argArray) {
        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(marker, format, argArray));
        }
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        if (shouldLog(Level.INFO)) {
            withMDC(() -> delegate.info(marker, msg, t));
        }
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(marker, msg));
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(marker, format, arg));
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(marker, format, arg1, arg2));
        }
    }

    @Override
    public void warn(Marker marker, String format, Object... argArray) {
        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(marker, format, argArray));
        }
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        if (shouldLog(Level.WARN)) {
            withMDC(() -> delegate.warn(marker, msg, t));
        }
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        handleError(() -> createLogEvent(Level.ERROR, msg, null, null));
        withMDC(() -> delegate.error(marker, msg));
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        handleError(() -> createLogEvent(Level.ERROR, format, new Object[]{arg}, null));
        withMDC(() -> delegate.error(marker, format, arg));
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        handleError(() -> createLogEvent(Level.ERROR, format, new Object[]{arg1, arg2}, null));
        withMDC(() -> delegate.error(marker, format, arg1, arg2));
    }

    @Override
    public void error(Marker marker, String format, Object... argArray) {
        handleError(() -> createLogEvent(Level.ERROR, format, argArray, null));
        withMDC(() -> delegate.error(marker, format, argArray));
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        handleError(() -> createLogEvent(Level.ERROR, msg, null, t));
        withMDC(() -> delegate.error(marker, msg, t));
    }

    // --- Private Helper Methods ---

    private void withMDC(Runnable action) {
        // Skip MDC wrapping when disabled - just run the action directly:
        if (!config.isEnabled()) {
            action.run();
            return;
        }
        MDContext.apply(mdcProvider.getCurrentContext(), action);
    }

    private LogEvent createLogEvent(Level level, String format, Object[] arguments, Throwable throwable) {
        logCount.incrementAndGet();

        return LogEvent.builder()
            .timestamp(Instant.now())
            .level(level)
            .loggerName(name)
            .threadName(Thread.currentThread().getName())
            .format(format)
            .arguments(arguments)
            .throwable(throwable)
            .mdContext(mdcProvider.getCurrentContext())
            .build();
    }

    private LogEvent createLazyLogEvent(Level level, String format, Supplier<?>[] argumentSuppliers, Throwable throwable) {
        logCount.incrementAndGet();

        return LogEvent.builder()
            .timestamp(Instant.now())
            .level(level)
            .loggerName(name)
            .threadName(Thread.currentThread().getName())
            .format(format)
            .lazyArguments(argumentSuppliers)
            .throwable(throwable)
            .mdContext(mdcProvider.getCurrentContext())
            .build();
    }

    private void handleError(Supplier<LogEvent> eventSupplier) {
        errorCount.incrementAndGet();
        levelController.handleError(
            eventSupplier,
            ringBufferManager::bufferEvent,
            this::dumpBuffer,
            delegate,
            name,
            this
        );
    }
}
