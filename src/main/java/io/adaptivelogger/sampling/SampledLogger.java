package io.adaptivelogger.sampling;

import io.adaptivelogger.IAdaptiveLogger;

import org.slf4j.event.Level;

import java.util.function.Supplier;

/**
 * Fluent API for sampled logging operations.
 *
 * <p>This class provides a fluent interface for sampling log messages to reduce
 * volume while maintaining statistical visibility. Created by calling
 * {@code IAdaptiveLogger.sample()} or {@code IAdaptiveLogger.sampleEvery()}.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Log ~10% of messages:
 * LOG.sample(0.1)
 *    .debug("High-volume event: {}", eventId);
 *
 * // Log every 100th message:
 * LOG.sampleEvery(100)
 *    .trace("Market tick: {}", tick);
 *
 * // Include count of sampled-out messages:
 * LOG.sample(0.1)
 *    .withSampledCount()
 *    .debug("Processing: {}", data);
 * }</pre>
 */
public class SampledLogger {
    private final IAdaptiveLogger logger;
    private final Sampler sampler;
    private final String messageKey;
    private boolean includeSampledCount = false;

    /**
     * Create a sampled logger with the given sampler.
     *
     * @param logger the parent IAdaptiveLogger
     * @param sampler the sampling strategy to use
     * @param messageKey optional key for grouping messages (if null, uses message format)
     */
    public SampledLogger(IAdaptiveLogger logger, Sampler sampler, String messageKey) {
        this.logger = logger;
        this.sampler = sampler;
        this.messageKey = messageKey;
    }

    /**
     * Include the count of sampled-out messages when logging.
     *
     * @return this instance for method chaining
     */
    public SampledLogger withSampledCount() {
        this.includeSampledCount = true;
        return this;
    }

    /**
     * Log a TRACE message with sampling.
     *
     * @param message the message to log
     */
    public void trace(String message) {
        logSampled(Level.TRACE, message, null, null);
    }

    /**
     * Log a TRACE message with sampling and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void trace(String format, Object... arguments) {
        logSampled(Level.TRACE, format, arguments, null);
    }

    /**
     * Log a DEBUG message with sampling.
     *
     * @param message the message to log
     */
    public void debug(String message) {
        logSampled(Level.DEBUG, message, null, null);
    }

    /**
     * Log a DEBUG message with sampling and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void debug(String format, Object... arguments) {
        logSampled(Level.DEBUG, format, arguments, null);
    }

    /**
     * Log an INFO message with sampling.
     *
     * @param message the message to log
     */
    public void info(String message) {
        logSampled(Level.INFO, message, null, null);
    }

    /**
     * Log an INFO message with sampling and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void info(String format, Object... arguments) {
        logSampled(Level.INFO, format, arguments, null);
    }

    /**
     * Log a WARN message with sampling.
     *
     * @param message the message to log
     */
    public void warn(String message) {
        logSampled(Level.WARN, message, null, null);
    }

    /**
     * Log a WARN message with sampling and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void warn(String format, Object... arguments) {
        logSampled(Level.WARN, format, arguments, null);
    }

    /**
     * Log an ERROR message with sampling.
     *
     * @param message the message to log
     */
    public void error(String message) {
        logSampled(Level.ERROR, message, null, null);
    }

    /**
     * Log an ERROR message with sampling and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void error(String format, Object... arguments) {
        logSampled(Level.ERROR, format, arguments, null);
    }

    /**
     * Log an ERROR message with sampling and exception.
     *
     * @param message the message to log
     * @param throwable the exception to log
     */
    public void error(String message, Throwable throwable) {
        logSampled(Level.ERROR, message, null, throwable);
    }

    /**
     * Log a message with lazy evaluation and sampling.
     *
     * @param level the log level
     * @param format the message format
     * @param argumentSuppliers suppliers for expensive arguments
     */
    public void lazy(Level level, String format, Supplier<?>... argumentSuppliers) {
        String key = messageKey != null ? messageKey : format;

        if (shouldLogSampled(level, key)) {
            // Evaluate suppliers only if we're going to log:
            Object[] arguments = new Object[argumentSuppliers.length];
            for (int i = 0; i < argumentSuppliers.length; i++) {
                arguments[i] = argumentSuppliers[i].get();
            }

            String finalMessage = formatMessageWithSampledCount(format, key);
            logger.logAtLevel(level, finalMessage, arguments);
        } else {
            sampler.recordSuppressed(key);
        }
    }

    private void logSampled(Level level, String format, Object[] arguments, Throwable throwable) {
        String key = messageKey != null ? messageKey : format;

        if (shouldLogSampled(level, key)) {
            String finalMessage = formatMessageWithSampledCount(format, key);

            if (throwable != null) {
                logger.logAtLevel(level, finalMessage, throwable);
            } else if (arguments != null && arguments.length > 0) {
                logger.logAtLevel(level, finalMessage, arguments);
            } else {
                logger.logAtLevel(level, finalMessage);
            }
        } else {
            sampler.recordSuppressed(key);
        }
    }

    private boolean shouldLogSampled(Level level, String key) {
        // Check adaptive log level first, then sampling:
        return logger.shouldLog(level) && sampler.shouldSample(key);
    }

    private String formatMessageWithSampledCount(String message, String key) {
        if (!includeSampledCount) {
            return message;
        }

        long sampledCount = sampler.getSuppressedCount(key);
        if (sampledCount > 0) {
            return message + " [~" + sampledCount + " similar messages sampled out]";
        }

        return message;
    }

    /**
     * Get the sampler used by this logger.
     * Default (package) visibility for testing sampler caching behavior.
     *
     * @return the sampler instance
     */
    public Sampler getSampler() {
        return sampler;
    }
}
