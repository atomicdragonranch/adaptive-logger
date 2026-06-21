package io.adaptivelogger;

import io.adaptivelogger.error.ErrorDetector;
import io.adaptivelogger.mdc.MDCProvider;
import io.adaptivelogger.model.LogEvent;
import io.adaptivelogger.model.MDContext;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the ring buffer for capturing and dumping log events.
 * Handles event buffering, cooldown-based dump rate limiting,
 * and formatted output of buffered events.
 */
class RingBufferManager {

    private final RingBuffer<LogEvent> debugBuffer;
    private final AdaptiveLoggingConfig config;

    // Rate limiting for buffer dumps:
    private final AtomicReference<Instant> lastBufferDump = new AtomicReference<>(Instant.MIN);

    RingBufferManager(AdaptiveLoggingConfig config) {
        this.config = config;
        this.debugBuffer = new RingBuffer<>(config.getRingBufferSize());
    }

    /**
     * Buffers a log event if buffering is enabled.
     *
     * @param event the log event to buffer
     */
    void bufferEvent(LogEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        if (config.isRingBufferEnabled()) {
            debugBuffer.add(event);
        }
    }

    /**
     * Dumps the ring buffer contents at INFO level via the delegate logger.
     * Respects cooldown period to prevent excessive dumps during error storms.
     *
     * @param delegate the underlying SLF4J logger for output
     * @param errorDetector the error detector to check error state
     */
    void dumpBuffer(Logger delegate, ErrorDetector errorDetector) {
        if (debugBuffer.isEmpty()) {
            return;
        }

        // Check cooldown to prevent excessive dumps during error storms:
        // Use CAS loop to avoid race condition where multiple threads pass the check:
        Instant now = Instant.now();

        // Zero cooldown means no rate limiting (useful for debugging):
        if (!config.getBufferDumpCooldown().isZero()) {
            while (true) {
                Instant lastDump = lastBufferDump.get();
                Duration timeSinceLastDump = Duration.between(lastDump, now);

                if (timeSinceLastDump.compareTo(config.getBufferDumpCooldown()) < 0) {
                    // Still in cooldown period, skip this dump:
                    long secondsRemaining = config.getBufferDumpCooldown().minus(timeSinceLastDump).getSeconds();

                    // Log at WARN if in error state (context may be lost), DEBUG otherwise:
                    if (errorDetector.isInErrorState()) {
                        delegate.warn("Buffer dump skipped due to cooldown ({}s remaining) - some debug context may be lost",
                            secondsRemaining);
                    } else {
                        delegate.debug("Buffer dump skipped due to cooldown ({}s remaining)", secondsRemaining);
                    }
                    return;
                }

                // Try to atomically update the last dump time:
                if (lastBufferDump.compareAndSet(lastDump, now)) {
                    // Won the race, proceed with dump:
                    break;
                }
                // Lost the race, another thread updated lastBufferDump:
                // Re-check to see if we're now in cooldown:
            }
        } else {
            // Zero cooldown - update timestamp for consistency if cooldown is later enabled:
            lastBufferDump.set(now);
        }

        // Perform the dump:
        delegate.info("=== Debug Buffer Dump Start (Size: {}) ===", debugBuffer.size());
        debugBuffer.forEach(event -> {
            // Log each buffered event at INFO level to ensure visibility:
            MDContext.apply(event.getMdContext(), () -> {
                String message = formatMessage(event);
                if (event.getThrowable() != null) {
                    delegate.info("[BUFFERED-{}] {}", event.getLevel(), message, event.getThrowable());
                } else {
                    delegate.info("[BUFFERED-{}] {}", event.getLevel(), message);
                }
            });
        });
        delegate.info("=== Debug Buffer Dump End ===");
    }

    /**
     * Clears all events from the ring buffer.
     */
    void clearBuffer() {
        debugBuffer.clear();
    }

    /**
     * Gets the current number of events in the ring buffer.
     *
     * @return the buffer size
     */
    int getBufferSize() {
        return debugBuffer.size();
    }

    /**
     * Gets the capacity of the ring buffer.
     *
     * @return the buffer capacity
     */
    int getBufferCapacity() {
        return debugBuffer.capacity();
    }

    private String formatMessage(LogEvent event) {
        if (event.getFormat() == null) {
            return "<no message>";
        }

        // Use getEvaluatedArguments() to handle both eager and lazy evaluation:
        Object[] args = event.getEvaluatedArguments();

        if (args == null || args.length == 0) {
            return event.getFormat();
        }

        // Use SLF4J's MessageFormatter to properly handle {} placeholders and % characters:
        try {
            return MessageFormatter.arrayFormat(event.getFormat(), args).getMessage();
        } catch (Exception e) {
            // Fallback to original format if formatting fails:
            return event.getFormat();
        }
    }
}
