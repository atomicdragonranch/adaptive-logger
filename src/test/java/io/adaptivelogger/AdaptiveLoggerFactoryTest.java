package io.adaptivelogger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveLoggerFactoryTest {

    @AfterEach
    void cleanup() {
        AdaptiveLoggerFactory.clear();
    }

    @Test
    void getLoggerByClass() {
        IAdaptiveLogger logger = AdaptiveLoggerFactory.getLogger(AdaptiveLoggerFactoryTest.class);
        assertNotNull(logger);
        assertEquals(AdaptiveLoggerFactoryTest.class.getName(), logger.getName());
    }

    @Test
    void getLoggerByName() {
        IAdaptiveLogger logger = AdaptiveLoggerFactory.getLogger("com.example.Test");
        assertNotNull(logger);
        assertEquals("com.example.Test", logger.getName());
    }

    @Test
    void sameNameReturnsSameInstance() {
        IAdaptiveLogger a = AdaptiveLoggerFactory.getLogger("shared");
        IAdaptiveLogger b = AdaptiveLoggerFactory.getLogger("shared");
        assertSame(a, b);
    }

    @Test
    void differentNamesReturnDifferentInstances() {
        IAdaptiveLogger a = AdaptiveLoggerFactory.getLogger("logger.a");
        IAdaptiveLogger b = AdaptiveLoggerFactory.getLogger("logger.b");
        assertNotSame(a, b);
    }

    @Test
    void explicitInitializationSetsConfig() {
        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.builder()
            .defaultLevel(Level.WARN)
            .escalationLevel(Level.DEBUG)
            .build();

        AdaptiveLoggerFactory.initialize(config);

        assertSame(config, AdaptiveLoggerFactory.getGlobalConfig());
    }

    @Test
    void reInitializationIsIdempotent() {
        AdaptiveLoggingConfig config1 = AdaptiveLoggingConfig.builder()
            .defaultLevel(Level.WARN)
            .escalationLevel(Level.DEBUG)
            .build();
        AdaptiveLoggingConfig config2 = AdaptiveLoggingConfig.builder()
            .defaultLevel(Level.ERROR)
            .escalationLevel(Level.WARN)
            .build();

        AdaptiveLoggerFactory.initialize(config1);
        AdaptiveLoggerFactory.initialize(config2);

        assertSame(config1, AdaptiveLoggerFactory.getGlobalConfig());
    }

    @Test
    void autoInitializesOnFirstGetLogger() {
        IAdaptiveLogger logger = AdaptiveLoggerFactory.getLogger("auto-init");
        assertNotNull(logger);
        assertNotNull(AdaptiveLoggerFactory.getGlobalConfig());
    }

    @Test
    void getAllLoggersReturnsActiveLogs() {
        AdaptiveLoggerFactory.getLogger("a");
        AdaptiveLoggerFactory.getLogger("b");
        AdaptiveLoggerFactory.getLogger("c");

        Map<String, IAdaptiveLogger> all = AdaptiveLoggerFactory.getAllLoggers();
        assertEquals(3, all.size());
        assertTrue(all.containsKey("a"));
        assertTrue(all.containsKey("b"));
        assertTrue(all.containsKey("c"));
    }

    @Test
    void getAllLoggersReturnsCopy() {
        AdaptiveLoggerFactory.getLogger("test");
        int sizeBefore = AdaptiveLoggerFactory.getAllLoggers().size();

        Map<String, IAdaptiveLogger> copy = AdaptiveLoggerFactory.getAllLoggers();
        copy.clear();

        assertEquals(sizeBefore, AdaptiveLoggerFactory.getAllLoggers().size());
    }

    @Test
    void clearRemovesAllLoggers() {
        AdaptiveLoggerFactory.getLogger("a");
        AdaptiveLoggerFactory.getLogger("b");

        AdaptiveLoggerFactory.clear();

        assertEquals(0, AdaptiveLoggerFactory.getAllLoggers().size());
    }

    @Test
    void updateGlobalConfigAffectsNewLoggers() {
        AdaptiveLoggerFactory.initialize(AdaptiveLoggingConfig.defaultConfig());

        AdaptiveLoggingConfig newConfig = AdaptiveLoggingConfig.builder()
            .defaultLevel(Level.ERROR)
            .escalationLevel(Level.WARN)
            .build();
        AdaptiveLoggerFactory.updateGlobalConfig(newConfig);

        assertSame(newConfig, AdaptiveLoggerFactory.getGlobalConfig());
    }

    @Test
    void resetAllLoggersResetsLevels() {
        AdaptiveLoggerFactory.initialize(AdaptiveLoggingConfig.defaultConfig());

        IAdaptiveLogger logger = AdaptiveLoggerFactory.getLogger("resettable");
        logger.setLevel(Level.TRACE);

        AdaptiveLoggerFactory.resetAllLoggers();
        assertEquals(Level.INFO, logger.getLevel());
    }
}
