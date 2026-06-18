package io.adaptivelogger.error;

import io.adaptivelogger.model.ErrorStatistics;
import io.adaptivelogger.model.LogEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects error patterns and determines when to escalate logging.
 * Uses a sliding time window to count errors and trigger escalation.
 */
public class ErrorDetector {

    private final ErrorDetectionConfig config;
    private final AtomicInteger errorCount;
    private final AtomicReference<Instant> windowStart;
    private final AtomicBoolean inErrorState;
    private final AtomicInteger escalationCount;
    private final AtomicReference<Instant> lastEscalation;

    public ErrorDetector(ErrorDetectionConfig config) {
        this.config = config;
        this.errorCount = new AtomicInteger(0);
        this.windowStart = new AtomicReference<>(Instant.now());
        this.inErrorState = new AtomicBoolean(false);
        this.escalationCount = new AtomicInteger(0);
        this.lastEscalation = new AtomicReference<>(null);
    }

    /**
     * Records an error event and checks if escalation should occur.
     *
     * @param event The log event representing the error
     */
    public void recordError(LogEvent event) {
        Instant now = Instant.now();
        Instant currentWindowStart = windowStart.get();

        // Check if we need to reset the window:
        if (now.isAfter(currentWindowStart.plus(config.getTimeWindow()))) {
            // Window expired, atomically get old count and reset to 1:
            int previousCount = errorCount.getAndSet(1);

            // Reset window:
            windowStart.set(now);

            // Clear error state only if previous window was below threshold:
            if (previousCount < config.getErrorThreshold()) {
                inErrorState.set(false);
            }
        } else {
            // Increment error count within current window:
            int newCount = errorCount.incrementAndGet();

            // Check if we've crossed the threshold:
            if (newCount >= config.getErrorThreshold()) {
                inErrorState.set(true);
            }
        }

        // Check for critical errors that trigger immediate escalation:
        if (config.isImmediateEscalationOnCritical() && event != null) {
            String errorMessage = extractErrorMessage(event);
            if (config.isCriticalError(errorMessage)) {
                inErrorState.set(true);
            }
        }
    }

    /**
     * Checks if logging should be escalated based on error patterns.
     * Respects the configured cooldown period to prevent log storm cycles.
     *
     * @return true if escalation should occur
     */
    public boolean shouldEscalate() {
        if (!inErrorState.get()) {
            return false;
        }

        // Check if we've already escalated recently:
        Instant lastEsc = lastEscalation.get();
        if (lastEsc != null) {
            // Check if enough time has passed since last escalation:
            Instant now = Instant.now();
            Duration timeSinceLastEscalation = Duration.between(lastEsc, now);

            if (timeSinceLastEscalation.compareTo(config.getEscalationCooldown()) < 0) {
                // Still in cooldown period, don't escalate:
                return false;
            }
        }

        // Record this escalation:
        escalationCount.incrementAndGet();
        lastEscalation.set(Instant.now());
        return true;
    }

    /**
     * Checks if currently in an error state.
     *
     * @return true if in error state
     */
    public boolean isInErrorState() {
        return inErrorState.get();
    }

    /**
     * Resets the error state after successful de-escalation.
     */
    public void resetErrorState() {
        inErrorState.set(false);
        lastEscalation.set(null);
    }

    /**
     * Gets the current error statistics.
     *
     * @return Error statistics
     */
    public ErrorStatistics getState() {
        Instant currentWindowStart = windowStart.get();
        return new ErrorStatistics(
            errorCount.get(),
            currentWindowStart,
            currentWindowStart.plus(config.getTimeWindow()),
            inErrorState.get(),
            escalationCount.get(),
            lastEscalation.get()
        );
    }

    /**
     * Extracts error message from log event for pattern matching.
     */
    private String extractErrorMessage(LogEvent event) {
        if (event == null) {
            return "";
        }

        StringBuilder message = new StringBuilder();

        // Add format message:
        if (event.getFormat() != null) {
            message.append(event.getFormat());
        }

        // Add throwable message and class name:
        if (event.getThrowable() != null) {
            message.append(" ")
                   .append(event.getThrowable().getClass().getName())
                   .append(": ");
            if (event.getThrowable().getMessage() != null) {
                message.append(event.getThrowable().getMessage());
            }
        }

        return message.toString();
    }
}
