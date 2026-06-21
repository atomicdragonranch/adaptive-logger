package io.adaptivelogger;

import io.adaptivelogger.error.ErrorDetector;
import io.adaptivelogger.model.LogEvent;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Controls log level management for an adaptive logger instance.
 * Handles level setting, resetting, escalation, de-escalation scheduling,
 * and the shouldLog decision based on current adaptive level.
 */
class LogLevelController {

    private final AtomicReference<Level> currentLevel;
    private final ErrorDetector errorDetector;
    private final AdaptiveLoggingConfig config;

    LogLevelController(AdaptiveLoggingConfig config) {
        this.config = config;
        this.currentLevel = new AtomicReference<>(config.getDefaultLevel());
        this.errorDetector = new ErrorDetector(config.getErrorDetectionConfig());
    }

    /**
     * Sets the current log level.
     *
     * @param level the new level
     * @param delegate the underlying SLF4J logger for change notification
     * @param loggerName the logger name for change notification
     */
    void setLevel(Level level, Logger delegate, String loggerName) {
        Level previousLevel = currentLevel.getAndSet(level);
        if (previousLevel != level) {
            delegate.info("Log level changed from {} to {} for logger: {}",
                previousLevel, level, loggerName);
        }
    }

    /**
     * Gets the current effective log level.
     *
     * @return the current level
     */
    Level getLevel() {
        return currentLevel.get();
    }

    /**
     * Resets the log level to the configured default.
     *
     * @param delegate the underlying SLF4J logger for change notification
     * @param loggerName the logger name for change notification
     */
    void resetLevel(Logger delegate, String loggerName) {
        setLevel(config.getDefaultLevel(), delegate, loggerName);
    }

    /**
     * Determines whether a message at the given level should be logged.
     * When adaptive logging is disabled, delegates to the underlying SLF4J logger.
     *
     * @param level the log level to check
     * @param delegate the underlying SLF4J logger (used when disabled)
     * @return true if the level is enabled for logging
     */
    boolean shouldLog(Level level, Logger delegate) {
        if (!config.isEnabled()) {
            return delegateLevelEnabled(level, delegate);
        }
        return level.toInt() >= currentLevel.get().toInt();
    }

    /**
     * Handles error detection, escalation, and optional buffer dump.
     *
     * @param eventSupplier supplies the error log event
     * @param bufferAction action to buffer the event
     * @param dumpAction action to dump the buffer
     */
    void handleError(Supplier<LogEvent> eventSupplier, java.util.function.Consumer<LogEvent> bufferAction,
                     Runnable dumpAction, Logger delegate, String loggerName, IAdaptiveLogger loggerRef) {
        if (!config.isEnabled()) {
            return;
        }

        LogEvent event = eventSupplier.get();
        bufferAction.accept(event);

        errorDetector.recordError(event);

        if (errorDetector.shouldEscalate()) {
            escalateLogging(delegate, loggerName, loggerRef);
        }

        if (config.isDumpBufferOnError() && errorDetector.isInErrorState()) {
            dumpAction.run();
        }
    }

    /**
     * Gets the error detector for statistics.
     *
     * @return the error detector
     */
    ErrorDetector getErrorDetector() {
        return errorDetector;
    }

    private void escalateLogging(Logger delegate, String loggerName, IAdaptiveLogger loggerRef) {
        Level escalationLevel = config.getEscalationLevel();
        if (currentLevel.get().toInt() > escalationLevel.toInt()) {
            delegate.warn("Escalating log level from {} to {} due to error threshold",
                currentLevel.get(), escalationLevel);
            setLevel(escalationLevel, delegate, loggerName);

            LogLevelScheduler.scheduleDeescalation(loggerRef, config.getEscalationDuration());
        }
    }

    private boolean delegateLevelEnabled(Level level, Logger delegate) {
        switch (level) {
            case TRACE:
                return delegate.isTraceEnabled();
            case DEBUG:
                return delegate.isDebugEnabled();
            case INFO:
                return delegate.isInfoEnabled();
            case WARN:
                return delegate.isWarnEnabled();
            case ERROR:
                return delegate.isErrorEnabled();
            default:
                return true;
        }
    }
}
