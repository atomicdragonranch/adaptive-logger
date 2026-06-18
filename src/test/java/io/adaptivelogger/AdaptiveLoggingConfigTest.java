package io.adaptivelogger;

import io.adaptivelogger.error.ErrorDetectionConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveLoggingConfigTest {

    @Test
    void defaultConfigHasSensibleValues() {
        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.defaultConfig();

        assertTrue(config.isEnabled());
        assertEquals(Level.INFO, config.getDefaultLevel());
        assertEquals(1000, config.getRingBufferSize());
        assertTrue(config.isRingBufferEnabled());
        assertTrue(config.isDumpBufferOnError());
        assertFalse(config.isBufferInfoMessages());
        assertEquals(Duration.ofSeconds(30), config.getBufferDumpCooldown());
        assertEquals(Level.DEBUG, config.getEscalationLevel());
        assertEquals(Duration.ofMinutes(5), config.getEscalationDuration());
        assertNotNull(config.getErrorDetectionConfig());
    }

    @Test
    void builderOverridesDefaults() {
        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.builder()
            .enabled(false)
            .defaultLevel(Level.WARN)
            .ringBufferSize(500)
            .ringBufferEnabled(false)
            .dumpBufferOnError(false)
            .bufferInfoMessages(true)
            .bufferDumpCooldown(Duration.ofSeconds(10))
            .escalationLevel(Level.TRACE)
            .escalationDuration(Duration.ofMinutes(10))
            .build();

        assertFalse(config.isEnabled());
        assertEquals(Level.WARN, config.getDefaultLevel());
        assertEquals(500, config.getRingBufferSize());
        assertFalse(config.isRingBufferEnabled());
        assertFalse(config.isDumpBufferOnError());
        assertTrue(config.isBufferInfoMessages());
        assertEquals(Duration.ofSeconds(10), config.getBufferDumpCooldown());
        assertEquals(Level.TRACE, config.getEscalationLevel());
        assertEquals(Duration.ofMinutes(10), config.getEscalationDuration());
    }

    @Test
    void escalationLevelMustBeMoreVerboseThanDefault() {
        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .defaultLevel(Level.DEBUG)
                .escalationLevel(Level.WARN)
                .build());
    }

    @Test
    void escalationLevelEqualToDefaultIsValid() {
        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder()
                .defaultLevel(Level.INFO)
                .escalationLevel(Level.INFO)
                .build());
    }

    @Test
    void ringBufferSizeValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder().ringBufferSize(0).build());

        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder().ringBufferSize(-1).build());

        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder().ringBufferSize(10001).build());
    }

    @Test
    void maxRingBufferSizeIsValid() {
        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder().ringBufferSize(10000).build());
    }

    @Test
    void bufferDumpCooldownValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .bufferDumpCooldown(Duration.ofSeconds(-1))
                .build());

        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .bufferDumpCooldown(null)
                .build());
    }

    @Test
    void zeroCooldownIsValid() {
        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder()
                .bufferDumpCooldown(Duration.ZERO)
                .build());
    }

    @Test
    void escalationDurationValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .escalationDuration(Duration.ZERO)
                .build());

        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .escalationDuration(Duration.ofSeconds(-1))
                .build());

        assertThrows(IllegalArgumentException.class, () ->
            AdaptiveLoggingConfig.builder()
                .escalationDuration(null)
                .build());
    }

    @Test
    void customErrorDetectionConfig() {
        ErrorDetectionConfig errorConfig = ErrorDetectionConfig.builder()
            .errorThreshold(10)
            .timeWindow(Duration.ofSeconds(120))
            .build();

        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.builder()
            .errorDetectionConfig(errorConfig)
            .build();

        assertSame(errorConfig, config.getErrorDetectionConfig());
    }

    @Test
    void toStringContainsKeyFields() {
        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.defaultConfig();
        String str = config.toString();

        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("defaultLevel=INFO"));
        assertTrue(str.contains("ringBufferSize=1000"));
        assertTrue(str.contains("escalationLevel=DEBUG"));
    }

    @Test
    void allLevelCombinationsWhereEscalationIsMoreVerbose() {
        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder()
                .defaultLevel(Level.ERROR)
                .escalationLevel(Level.TRACE)
                .build());

        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder()
                .defaultLevel(Level.WARN)
                .escalationLevel(Level.DEBUG)
                .build());

        assertDoesNotThrow(() ->
            AdaptiveLoggingConfig.builder()
                .defaultLevel(Level.INFO)
                .escalationLevel(Level.TRACE)
                .build());
    }
}
