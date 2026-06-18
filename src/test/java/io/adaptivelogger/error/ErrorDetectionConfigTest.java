package io.adaptivelogger.error;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ErrorDetectionConfigTest {

    @Test
    void defaultConfigHasBasePatterns() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();

        assertEquals(5, config.getErrorThreshold());
        assertEquals(Duration.ofSeconds(60), config.getTimeWindow());
        assertTrue(config.isImmediateEscalationOnCritical());
        assertFalse(config.getCriticalErrorPatterns().isEmpty());
    }

    @Test
    void defaultConfigMatchesOOM() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertTrue(config.isCriticalError("java.lang.OutOfMemoryError: Java heap space"));
    }

    @Test
    void defaultConfigMatchesStackOverflow() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertTrue(config.isCriticalError("java.lang.StackOverflowError"));
    }

    @Test
    void defaultConfigMatchesCheckpointFailure() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertTrue(config.isCriticalError("CheckpointException during savepoint"));
        assertTrue(config.isCriticalError("checkpoint failed at barrier"));
    }

    @Test
    void defaultConfigMatchesSerializationError() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertTrue(config.isCriticalError("SerializationException in KafkaConsumer"));
        assertTrue(config.isCriticalError("Failed to deserialize message"));
    }

    @Test
    void defaultConfigMatchesStateCorruption() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertTrue(config.isCriticalError("StateBackendException: rocksdb error"));
        assertTrue(config.isCriticalError("state corruption detected"));
        assertTrue(config.isCriticalError("state inconsistency found"));
    }

    @Test
    void nonCriticalMessageDoesNotMatch() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertFalse(config.isCriticalError("Connection timeout after 30s"));
        assertFalse(config.isCriticalError("User not found"));
    }

    @Test
    void nullMessageReturnsFalse() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertFalse(config.isCriticalError(null));
    }

    @Test
    void customPatternViaBuilder() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .addCriticalPattern("(?i).*KafkaTimeoutException.*")
            .build();

        assertTrue(config.isCriticalError("org.apache.kafka.KafkaTimeoutException"));
        assertFalse(config.isCriticalError("regular error"));
    }

    @Test
    void customPatternFromCompiledRegex() {
        Pattern pattern = Pattern.compile(".*custom_error_code_42.*");
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .addCriticalPattern(pattern)
            .build();

        assertTrue(config.isCriticalError("got custom_error_code_42 from server"));
    }

    @Test
    void builderValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            ErrorDetectionConfig.builder().errorThreshold(0).build());

        assertThrows(IllegalArgumentException.class, () ->
            ErrorDetectionConfig.builder().errorThreshold(-1).build());

        assertThrows(IllegalArgumentException.class, () ->
            ErrorDetectionConfig.builder().timeWindow(Duration.ZERO).build());

        assertThrows(IllegalArgumentException.class, () ->
            ErrorDetectionConfig.builder().timeWindow(Duration.ofSeconds(-1)).build());

        assertThrows(IllegalArgumentException.class, () ->
            ErrorDetectionConfig.builder().escalationCooldown(Duration.ofSeconds(-1)).build());
    }

    @Test
    void zeroCooldownIsValid() {
        assertDoesNotThrow(() ->
            ErrorDetectionConfig.builder()
                .escalationCooldown(Duration.ZERO)
                .build());
    }

    @Test
    void emptyPatternListNeverMatches() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .immediateEscalationOnCritical(true)
            .build();

        assertFalse(config.isCriticalError("OutOfMemoryError"));
    }

    @Test
    void nullPatternsAreIgnored() {
        assertDoesNotThrow(() ->
            ErrorDetectionConfig.builder()
                .addCriticalPattern((String) null)
                .addCriticalPattern("")
                .addCriticalPattern((Pattern) null)
                .build());
    }

    @Test
    void patternsAreImmutable() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        assertThrows(UnsupportedOperationException.class, () ->
            config.getCriticalErrorPatterns().add(Pattern.compile(".*")));
    }

    @Test
    void toStringIncludesKey() {
        ErrorDetectionConfig config = ErrorDetectionConfig.defaultConfig();
        String str = config.toString();
        assertTrue(str.contains("errorThreshold=5"));
        assertTrue(str.contains("criticalErrorPatternsCount="));
    }
}
