package io.adaptivelogger;

import io.adaptivelogger.error.ErrorDetectionConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import java.io.Serializable;
import java.time.Duration;

/**
 * Configuration for the adaptive logging system.
 * Supports initialization from environment variables for easy deployment configuration.
 */
public class AdaptiveLoggingConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveLoggingConfig.class);

    private final boolean enabled;
    private final Level defaultLevel;
    private final int ringBufferSize;
    private final boolean ringBufferEnabled;
    private final boolean dumpBufferOnError;
    private final boolean bufferInfoMessages;
    private final Duration bufferDumpCooldown;
    private final ErrorDetectionConfig errorDetectionConfig;
    private final Level escalationLevel;
    private final Duration escalationDuration;

    private AdaptiveLoggingConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.defaultLevel = builder.defaultLevel;
        this.ringBufferSize = builder.ringBufferSize;
        this.ringBufferEnabled = builder.ringBufferEnabled;
        this.dumpBufferOnError = builder.dumpBufferOnError;
        this.bufferInfoMessages = builder.bufferInfoMessages;
        this.bufferDumpCooldown = builder.bufferDumpCooldown;
        this.errorDetectionConfig = builder.errorDetectionConfig;
        this.escalationLevel = builder.escalationLevel;
        this.escalationDuration = builder.escalationDuration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Level getDefaultLevel() {
        return defaultLevel;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public boolean isRingBufferEnabled() {
        return ringBufferEnabled;
    }

    public boolean isDumpBufferOnError() {
        return dumpBufferOnError;
    }

    public boolean isBufferInfoMessages() {
        return bufferInfoMessages;
    }

    public Duration getBufferDumpCooldown() {
        return bufferDumpCooldown;
    }

    public ErrorDetectionConfig getErrorDetectionConfig() {
        return errorDetectionConfig;
    }

    public Level getEscalationLevel() {
        return escalationLevel;
    }

    public Duration getEscalationDuration() {
        return escalationDuration;
    }

    /**
     * Creates a default configuration suitable for production use.
     */
    public static AdaptiveLoggingConfig defaultConfig() {
        return builder()
            .enabled(true)
            .defaultLevel(Level.INFO)
            .ringBufferSize(1000)
            .ringBufferEnabled(true)
            .dumpBufferOnError(true)
            .bufferInfoMessages(false)
            .bufferDumpCooldown(Duration.ofSeconds(30))
            .errorDetectionConfig(ErrorDetectionConfig.defaultConfig())
            .escalationLevel(Level.DEBUG)
            .escalationDuration(Duration.ofMinutes(5))
            .build();
    }

    /**
     * Creates configuration from environment variables.
     * Environment variables:
     * - ADAPTIVE_LOGGING_ENABLED (true/false, default: true)
     * - ADAPTIVE_LOGGING_DEFAULT_LEVEL (TRACE/DEBUG/INFO/WARN/ERROR, default: INFO)
     * - ADAPTIVE_LOGGING_BUFFER_SIZE (integer, default: 1000)
     * - ADAPTIVE_LOGGING_DUMP_ON_ERROR (true/false, default: true)
     * - ADAPTIVE_LOGGING_BUFFER_INFO (true/false, default: false)
     * - ADAPTIVE_LOGGING_BUFFER_DUMP_COOLDOWN_SECONDS (integer, default: 30)
     * - ADAPTIVE_LOGGING_ERROR_THRESHOLD (integer, default: 5)
     * - ADAPTIVE_LOGGING_ERROR_WINDOW_SECONDS (integer, default: 60)
     * - ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_SECONDS (integer, default: 600)
     * - ADAPTIVE_LOGGING_ESCALATION_LEVEL (TRACE/DEBUG/INFO/WARN/ERROR, default: DEBUG)
     * - ADAPTIVE_LOGGING_ESCALATION_DURATION_SECONDS (integer, default: 300)
     */
    public static AdaptiveLoggingConfig fromEnv() {
        Builder builder = builder();

        // Core settings:
        String enabled = System.getenv("ADAPTIVE_LOGGING_ENABLED");
        if (enabled != null) {
            builder.enabled(Boolean.parseBoolean(enabled));
        }

        String defaultLevel = System.getenv("ADAPTIVE_LOGGING_DEFAULT_LEVEL");
        if (defaultLevel != null) {
            builder.defaultLevel(parseLevel(defaultLevel));
        }

        // Ring buffer settings:
        String bufferSize = System.getenv("ADAPTIVE_LOGGING_BUFFER_SIZE");
        if (bufferSize != null) {
            try {
                builder.ringBufferSize(Integer.parseInt(bufferSize));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_BUFFER_SIZE '{}', using default", bufferSize);
            }
        }

        String dumpOnError = System.getenv("ADAPTIVE_LOGGING_DUMP_ON_ERROR");
        if (dumpOnError != null) {
            builder.dumpBufferOnError(Boolean.parseBoolean(dumpOnError));
        }

        String bufferInfo = System.getenv("ADAPTIVE_LOGGING_BUFFER_INFO");
        if (bufferInfo != null) {
            builder.bufferInfoMessages(Boolean.parseBoolean(bufferInfo));
        }

        String bufferDumpCooldown = System.getenv("ADAPTIVE_LOGGING_BUFFER_DUMP_COOLDOWN_SECONDS");
        if (bufferDumpCooldown != null) {
            try {
                builder.bufferDumpCooldown(Duration.ofSeconds(Long.parseLong(bufferDumpCooldown)));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_BUFFER_DUMP_COOLDOWN_SECONDS '{}', using default", bufferDumpCooldown);
            }
        }

        // Error detection settings:
        ErrorDetectionConfig.Builder errorBuilder = ErrorDetectionConfig.builder();

        String errorThreshold = System.getenv("ADAPTIVE_LOGGING_ERROR_THRESHOLD");
        if (errorThreshold != null) {
            try {
                errorBuilder.errorThreshold(Integer.parseInt(errorThreshold));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_ERROR_THRESHOLD '{}', using default", errorThreshold);
            }
        }

        String errorWindow = System.getenv("ADAPTIVE_LOGGING_ERROR_WINDOW_SECONDS");
        if (errorWindow != null) {
            try {
                errorBuilder.timeWindow(Duration.ofSeconds(Long.parseLong(errorWindow)));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_ERROR_WINDOW_SECONDS '{}', using default", errorWindow);
            }
        }

        String escalationCooldown = System.getenv("ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_SECONDS");
        if (escalationCooldown != null) {
            try {
                errorBuilder.escalationCooldown(Duration.ofSeconds(Long.parseLong(escalationCooldown)));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_SECONDS '{}', using default", escalationCooldown);
            }
        }

        builder.errorDetectionConfig(errorBuilder.build());

        // Escalation settings:
        String escalationLevel = System.getenv("ADAPTIVE_LOGGING_ESCALATION_LEVEL");
        if (escalationLevel != null) {
            builder.escalationLevel(parseLevel(escalationLevel));
        }

        String escalationDuration = System.getenv("ADAPTIVE_LOGGING_ESCALATION_DURATION_SECONDS");
        if (escalationDuration != null) {
            try {
                builder.escalationDuration(Duration.ofSeconds(Long.parseLong(escalationDuration)));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid ADAPTIVE_LOGGING_ESCALATION_DURATION_SECONDS '{}', using default", escalationDuration);
            }
        }

        return builder.build();
    }

    private static Level parseLevel(String levelStr) {
        try {
            return Level.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private Level defaultLevel = Level.INFO;
        private int ringBufferSize = 1000;
        private boolean ringBufferEnabled = true;
        private boolean dumpBufferOnError = true;
        private boolean bufferInfoMessages = false;
        private Duration bufferDumpCooldown = Duration.ofSeconds(30);
        private ErrorDetectionConfig errorDetectionConfig = ErrorDetectionConfig.defaultConfig();
        private Level escalationLevel = Level.DEBUG;
        private Duration escalationDuration = Duration.ofMinutes(5);

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder defaultLevel(Level defaultLevel) {
            this.defaultLevel = defaultLevel;
            return this;
        }

        public Builder ringBufferSize(int ringBufferSize) {
            if (ringBufferSize <= 0) {
                throw new IllegalArgumentException("Ring buffer size must be positive");
            }
            // Memory estimation: ~1KB per LogEvent (format, args, MDC, throwable):
            // 1000 events ~1MB, 10000 events ≈ 10MB per logger instance:
            if (ringBufferSize > 10000) {
                throw new IllegalArgumentException("Ring buffer size must not exceed 10000 to prevent memory exhaustion");
            }
            this.ringBufferSize = ringBufferSize;
            return this;
        }

        public Builder ringBufferEnabled(boolean ringBufferEnabled) {
            this.ringBufferEnabled = ringBufferEnabled;
            return this;
        }

        public Builder dumpBufferOnError(boolean dumpBufferOnError) {
            this.dumpBufferOnError = dumpBufferOnError;
            return this;
        }

        public Builder bufferInfoMessages(boolean bufferInfoMessages) {
            this.bufferInfoMessages = bufferInfoMessages;
            return this;
        }

        public Builder bufferDumpCooldown(Duration bufferDumpCooldown) {
            if (bufferDumpCooldown == null || bufferDumpCooldown.isNegative()) {
                throw new IllegalArgumentException("Buffer dump cooldown must be non-negative (zero means no rate limiting)");
            }
            this.bufferDumpCooldown = bufferDumpCooldown;
            return this;
        }

        public Builder errorDetectionConfig(ErrorDetectionConfig errorDetectionConfig) {
            this.errorDetectionConfig = errorDetectionConfig;
            return this;
        }

        public Builder escalationLevel(Level escalationLevel) {
            this.escalationLevel = escalationLevel;
            return this;
        }

        public Builder escalationDuration(Duration escalationDuration) {
            if (escalationDuration == null || escalationDuration.isNegative() || escalationDuration.isZero()) {
                throw new IllegalArgumentException("Escalation duration must be positive");
            }
            this.escalationDuration = escalationDuration;
            return this;
        }

        public AdaptiveLoggingConfig build() {
            // Validate that escalation level is more verbose than default level:
            // (lower int value = more verbose: TRACE=0, DEBUG=10, INFO=20, WARN=30, ERROR=40)
            if (escalationLevel.toInt() > defaultLevel.toInt()) {
                throw new IllegalArgumentException(
                    "Escalation level (" + escalationLevel + ") must be more verbose than or equal to " +
                    "default level (" + defaultLevel + "). Escalation should increase verbosity, not decrease it.");
            }
            return new AdaptiveLoggingConfig(this);
        }
    }

    @Override
    public String toString() {
        return "AdaptiveLoggingConfig{" +
               "enabled=" + enabled +
               ", defaultLevel=" + defaultLevel +
               ", ringBufferSize=" + ringBufferSize +
               ", ringBufferEnabled=" + ringBufferEnabled +
               ", dumpBufferOnError=" + dumpBufferOnError +
               ", bufferInfoMessages=" + bufferInfoMessages +
               ", errorDetectionConfig=" + errorDetectionConfig +
               ", escalationLevel=" + escalationLevel +
               ", escalationDuration=" + escalationDuration +
               '}';
    }
}
