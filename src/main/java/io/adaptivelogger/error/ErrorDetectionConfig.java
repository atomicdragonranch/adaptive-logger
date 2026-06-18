package io.adaptivelogger.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration for error detection and escalation behavior.
 *
 * Supports two modes of pattern configuration:
 * 1. BASE_PATTERNS: Always active, covers catastrophic failures
 * 2. Additional patterns: Loaded from ADAPTIVE_LOGGING_CRITICAL_PATTERNS environment variable
 *
 * This hybrid approach provides:
 * - Safety: Core patterns are tested and always present
 * - Flexibility: Environment-specific patterns can be added without code changes
 * - Operations: SREs can respond to new failure modes quickly
 */
public class ErrorDetectionConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ErrorDetectionConfig.class);

    /**
     * Base critical error patterns that are ALWAYS active.
     * These patterns cover catastrophic failures that require immediate escalation:
     * - JVM fatal errors (OutOfMemory, StackOverflow)
     * - State corruption (data integrity)
     * - Checkpoint failures (exactly-once guarantees lost)
     * - Serialization failures (cannot process events)
     *
     * All patterns use (?i) for case-insensitive matching to catch errors
     * regardless of how they're formatted in logs.
     */
    private static final String[] BASE_PATTERNS = {
        // JVM fatal errors (cannot recover):
        "(?i).*OutOfMemoryError.*",
        "(?i).*StackOverflowError.*",
        "(?i).*NoClassDefFoundError.*",
        "(?i).*InternalError.*",

        // State corruption (data integrity):
        "(?i).*StateBackendException.*",
        "(?i).*state.*corrupt.*",
        "(?i).*state.*inconsisten.*",
        "(?i).*StateMigrationException.*",

        // Checkpoint failures (exactly-once lost):
        "(?i).*CheckpointException.*",
        "(?i).*checkpoint.*failed.*",
        "(?i).*checkpoint.*expired.*",

        // Serialization failures (cannot process events):
        "(?i).*SerializationException.*",
        "(?i).*InvalidProtocolBufferException.*",
        "(?i).*Failed to deserialize.*"
    };

    private final int errorThreshold;
    private final Duration timeWindow;
    private final List<Pattern> criticalErrorPatterns;
    private final boolean immediateEscalationOnCritical;
    private final Duration escalationCooldown;

    private ErrorDetectionConfig(Builder builder) {
        this.errorThreshold = builder.errorThreshold;
        this.timeWindow = builder.timeWindow;
        this.criticalErrorPatterns = Collections.unmodifiableList(new ArrayList<>(builder.criticalErrorPatterns));
        this.immediateEscalationOnCritical = builder.immediateEscalationOnCritical;
        this.escalationCooldown = builder.escalationCooldown;
    }

    public int getErrorThreshold() {
        return errorThreshold;
    }

    public Duration getTimeWindow() {
        return timeWindow;
    }

    public List<Pattern> getCriticalErrorPatterns() {
        return criticalErrorPatterns;
    }

    public boolean isImmediateEscalationOnCritical() {
        return immediateEscalationOnCritical;
    }

    public Duration getEscalationCooldown() {
        return escalationCooldown;
    }

    /**
     * Checks if an error message matches any critical error pattern.
     */
    public boolean isCriticalError(String message) {
        if (message == null || criticalErrorPatterns.isEmpty()) {
            return false;
        }

        for (Pattern pattern : criticalErrorPatterns) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a default configuration with BASE_PATTERNS only.
     * Use fromEnvironment() to include environment-specific patterns.
     */
    public static ErrorDetectionConfig defaultConfig() {
        Builder builder = builder()
            .errorThreshold(5)
            .timeWindow(Duration.ofSeconds(60))
            .immediateEscalationOnCritical(true)
            .escalationCooldown(Duration.ofMinutes(10));

        // Add base patterns:
        for (String pattern : BASE_PATTERNS) {
            builder.addCriticalPattern(pattern);
        }

        return builder.build();
    }

    /**
     * Creates configuration from environment variables.
     * Includes BASE_PATTERNS plus additional patterns from ADAPTIVE_LOGGING_CRITICAL_PATTERNS.
     *
     * Environment variables:
     * - ADAPTIVE_LOGGING_ERROR_THRESHOLD: Number of errors to trigger escalation (default: 5)
     * - ADAPTIVE_LOGGING_TIME_WINDOW_SECONDS: Sliding window duration (default: 60)
     * - ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_MINUTES: Cooldown period (default: 10)
     * - ADAPTIVE_LOGGING_CRITICAL_PATTERNS: Semicolon-separated additional patterns
     *
     * Example:
     * ADAPTIVE_LOGGING_CRITICAL_PATTERNS=".*Kafka.*TimeoutException.*;.*OffsetOutOfRangeException.*"
     *
     * @return Configuration with base patterns plus environment-specific patterns
     * @throws IllegalArgumentException if any pattern has invalid regex syntax
     */
    public static ErrorDetectionConfig fromEnvironment() {
        Builder builder = builder()
            .errorThreshold(getEnvInt("ADAPTIVE_LOGGING_ERROR_THRESHOLD", 5))
            .timeWindow(Duration.ofSeconds(getEnvInt("ADAPTIVE_LOGGING_TIME_WINDOW_SECONDS", 60)))
            .immediateEscalationOnCritical(true)
            .escalationCooldown(Duration.ofMinutes(getEnvInt("ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_MINUTES", 10)));

        // Add base patterns (always active):
        LOG.info("Loading {} base critical error patterns", BASE_PATTERNS.length);
        for (String pattern : BASE_PATTERNS) {
            builder.addCriticalPattern(pattern);
        }

        // Add additional patterns from environment:
        String additionalPatterns = System.getenv("ADAPTIVE_LOGGING_CRITICAL_PATTERNS");
        if (additionalPatterns != null && !additionalPatterns.trim().isEmpty()) {
            String[] patterns = additionalPatterns.split(";");
            LOG.info("Loading {} additional critical error patterns from ADAPTIVE_LOGGING_CRITICAL_PATTERNS", patterns.length);

            for (String pattern : patterns) {
                String trimmedPattern = pattern.trim();
                if (!trimmedPattern.isEmpty()) {
                    try {
                        // Validate pattern syntax by compiling it:
                        Pattern.compile(trimmedPattern);
                        builder.addCriticalPattern(trimmedPattern);
                        LOG.debug("Added critical error pattern: {}", trimmedPattern);
                    } catch (PatternSyntaxException e) {
                        String errorMsg = String.format("Invalid regex syntax in ADAPTIVE_LOGGING_CRITICAL_PATTERNS: '%s' - %s",
                            trimmedPattern, e.getMessage());
                        LOG.error(errorMsg);
                        throw new IllegalArgumentException(errorMsg, e);
                    }
                }
            }
        } else {
            LOG.info("No additional critical error patterns configured (ADAPTIVE_LOGGING_CRITICAL_PATTERNS not set)");
        }

        ErrorDetectionConfig config = builder.build();
        LOG.info("Loaded error detection config: {}", config);
        return config;
    }

    /**
     * Helper method to read integer from environment with default value.
     */
    private static int getEnvInt(String envVar, int defaultValue) {
        String value = System.getenv(envVar);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value for {}: '{}', using default: {}", envVar, value, defaultValue);
            return defaultValue;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int errorThreshold = 5;
        private Duration timeWindow = Duration.ofSeconds(60);
        private List<Pattern> criticalErrorPatterns = new ArrayList<>();
        private boolean immediateEscalationOnCritical = true;
        private Duration escalationCooldown = Duration.ofMinutes(10);

        public Builder errorThreshold(int errorThreshold) {
            if (errorThreshold <= 0) {
                throw new IllegalArgumentException("Error threshold must be positive");
            }
            this.errorThreshold = errorThreshold;
            return this;
        }

        public Builder timeWindow(Duration timeWindow) {
            if (timeWindow == null || timeWindow.isNegative() || timeWindow.isZero()) {
                throw new IllegalArgumentException("Time window must be positive");
            }
            this.timeWindow = timeWindow;
            return this;
        }

        public Builder addCriticalPattern(String regex) {
            if (regex != null && !regex.isEmpty()) {
                criticalErrorPatterns.add(Pattern.compile(regex));
            }
            return this;
        }

        public Builder addCriticalPattern(Pattern pattern) {
            if (pattern != null) {
                criticalErrorPatterns.add(pattern);
            }
            return this;
        }

        public Builder immediateEscalationOnCritical(boolean immediateEscalationOnCritical) {
            this.immediateEscalationOnCritical = immediateEscalationOnCritical;
            return this;
        }

        public Builder escalationCooldown(Duration escalationCooldown) {
            if (escalationCooldown == null || escalationCooldown.isNegative()) {
                throw new IllegalArgumentException("Escalation cooldown must be non-negative (zero means no cooldown)");
            }
            this.escalationCooldown = escalationCooldown;
            return this;
        }

        public ErrorDetectionConfig build() {
            return new ErrorDetectionConfig(this);
        }
    }

    @Override
    public String toString() {
        return "ErrorDetectionConfig{" +
               "errorThreshold=" + errorThreshold +
               ", timeWindow=" + timeWindow +
               ", criticalErrorPatternsCount=" + criticalErrorPatterns.size() +
               ", immediateEscalationOnCritical=" + immediateEscalationOnCritical +
               ", escalationCooldown=" + escalationCooldown +
               '}';
    }
}
