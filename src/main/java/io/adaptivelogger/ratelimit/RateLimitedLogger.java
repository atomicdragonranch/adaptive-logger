package io.adaptivelogger.ratelimit;

import io.adaptivelogger.IAdaptiveLogger;

import org.slf4j.event.Level;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fluent API for rate-limited logging operations.
 *
 * <p>This class provides a Google Flogger-style interface for rate limiting
 * log messages. It's created by calling {@code IAdaptiveLogger.atMostEvery()}.
 *
 * <p>Example usage:
 * <pre>{@code
 * LOG.atMostEvery(30, SECONDS)
 *    .warn("Connection pool exhausted for {}", poolName);
 *
 * LOG.atMostEvery(1, MINUTES)
 *    .withSuppressedCount()
 *    .error("Critical system failure", exception);
 * }</pre>
 */
public class RateLimitedLogger {
    private final IAdaptiveLogger logger;
    private final Duration window;
    private final String messageKey;
    private boolean includeSuppressedCount = false;

    /**
     * Create a rate-limited logger for a specific time window.
     *
     * @param logger the parent IAdaptiveLogger
     * @param window the time window for rate limiting
     * @param messageKey optional key for grouping messages (if null, uses message format)
     */
    public RateLimitedLogger(IAdaptiveLogger logger, Duration window, String messageKey) {
        this.logger = logger;
        this.window = window;
        this.messageKey = messageKey;
    }

    /**
     * Include the count of suppressed messages when logging.
     *
     * @return this instance for method chaining
     */
    public RateLimitedLogger withSuppressedCount() {
        this.includeSuppressedCount = true;
        return this;
    }

    /**
     * Log a TRACE message with rate limiting.
     *
     * @param message the message to log
     */
    public void trace(String message) {
        logRateLimited(Level.TRACE, message, null, null);
    }

    /**
     * Log a TRACE message with rate limiting and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void trace(String format, Object... arguments) {
        logRateLimited(Level.TRACE, format, arguments, null);
    }

    /**
     * Log a DEBUG message with rate limiting.
     *
     * @param message the message to log
     */
    public void debug(String message) {
        logRateLimited(Level.DEBUG, message, null, null);
    }

    /**
     * Log a DEBUG message with rate limiting and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void debug(String format, Object... arguments) {
        logRateLimited(Level.DEBUG, format, arguments, null);
    }

    /**
     * Log an INFO message with rate limiting.
     *
     * @param message the message to log
     */
    public void info(String message) {
        logRateLimited(Level.INFO, message, null, null);
    }

    /**
     * Log an INFO message with rate limiting and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void info(String format, Object... arguments) {
        logRateLimited(Level.INFO, format, arguments, null);
    }

    /**
     * Log a WARN message with rate limiting.
     *
     * @param message the message to log
     */
    public void warn(String message) {
        logRateLimited(Level.WARN, message, null, null);
    }

    /**
     * Log a WARN message with rate limiting and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void warn(String format, Object... arguments) {
        logRateLimited(Level.WARN, format, arguments, null);
    }

    /**
     * Log an ERROR message with rate limiting.
     *
     * @param message the message to log
     */
    public void error(String message) {
        logRateLimited(Level.ERROR, message, null, null);
    }

    /**
     * Log an ERROR message with rate limiting and formatting.
     *
     * @param format the message format
     * @param arguments the format arguments
     */
    public void error(String format, Object... arguments) {
        logRateLimited(Level.ERROR, format, arguments, null);
    }

    /**
     * Log an ERROR message with rate limiting and exception.
     *
     * @param message the message to log
     * @param throwable the exception to log
     */
    public void error(String message, Throwable throwable) {
        logRateLimited(Level.ERROR, message, null, throwable);
    }

    /**
     * Log a message with lazy evaluation and rate limiting.
     *
     * @param level the log level
     * @param format the message format
     * @param argumentSuppliers suppliers for expensive arguments
     */
    public void lazy(Level level, String format, Supplier<?>... argumentSuppliers) {
        String key = messageKey != null ? messageKey : format;

        if (logger.shouldLogWithRateLimit(level, key, window)) {
            // Evaluate suppliers only if we're going to log:
            Object[] arguments = new Object[argumentSuppliers.length];
            for (int i = 0; i < argumentSuppliers.length; i++) {
                arguments[i] = argumentSuppliers[i].get();
            }

            String finalMessage = formatMessageWithSuppressedCount(format, key);
            logger.logAtLevel(level, finalMessage, arguments);
        } else {
            logger.getRateLimiter().recordSuppressed(key);
        }
    }

    private void logRateLimited(Level level, String format, Object[] arguments, Throwable throwable) {
        String key = messageKey != null ? messageKey : format;

        if (logger.shouldLogWithRateLimit(level, key, window)) {
            String finalMessage = formatMessageWithSuppressedCount(format, key);

            if (throwable != null) {
                logger.logAtLevel(level, finalMessage, throwable);
            } else if (arguments != null && arguments.length > 0) {
                logger.logAtLevel(level, finalMessage, arguments);
            } else {
                logger.logAtLevel(level, finalMessage);
            }
        } else {
            logger.getRateLimiter().recordSuppressed(key);
        }
    }

    private String formatMessageWithSuppressedCount(String message, String key) {
        if (!includeSuppressedCount) {
            return message;
        }

        long suppressedCount = logger.getRateLimiter().getSuppressedCount(key);
        if (suppressedCount > 0) {
            return message + " [" + suppressedCount + " similar messages suppressed]";
        }

        return message;
    }
}
