package io.adaptivelogger.model;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.io.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LogEventTest {

    @Test
    void eagerEventReturnsArgumentsDirectly() {
        Object[] args = {"hello", 42};
        LogEvent event = LogEvent.builder()
            .timestamp(Instant.now())
            .level(Level.INFO)
            .loggerName("test")
            .threadName("main")
            .format("msg {} {}")
            .arguments(args)
            .build();

        assertFalse(event.isLazy());
        assertSame(args, event.getEvaluatedArguments());
    }

    @Test
    void lazyEventEvaluatesOnDemand() {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<?>[] suppliers = {
            () -> { callCount.incrementAndGet(); return "evaluated"; }
        };

        LogEvent event = LogEvent.builder()
            .timestamp(Instant.now())
            .level(Level.DEBUG)
            .loggerName("test")
            .threadName("main")
            .format("lazy: {}")
            .lazyArguments(suppliers)
            .build();

        assertTrue(event.isLazy());
        assertEquals(0, callCount.get());

        Object[] result = event.getEvaluatedArguments();
        assertEquals("evaluated", result[0]);
        assertEquals(1, callCount.get());
    }

    @Test
    void lazyEvaluationIsCached() {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<?>[] suppliers = {
            () -> { callCount.incrementAndGet(); return "value"; }
        };

        LogEvent event = LogEvent.builder()
            .level(Level.INFO)
            .format("{}")
            .lazyArguments(suppliers)
            .build();

        event.getEvaluatedArguments();
        event.getEvaluatedArguments();
        event.getEvaluatedArguments();

        assertEquals(1, callCount.get());
    }

    @Test
    void lazySupplierExceptionIsCaught() {
        Supplier<?>[] suppliers = {
            () -> { throw new RuntimeException("boom"); }
        };

        LogEvent event = LogEvent.builder()
            .level(Level.ERROR)
            .format("{}")
            .lazyArguments(suppliers)
            .build();

        Object[] result = event.getEvaluatedArguments();
        assertTrue(result[0].toString().contains("Error evaluating argument"));
        assertTrue(result[0].toString().contains("boom"));
    }

    @Test
    void nullLazyArgumentsReturnsNull() {
        LogEvent event = LogEvent.builder()
            .level(Level.INFO)
            .format("no args")
            .lazyArguments(null)
            .build();

        // isLazy is set to true by lazyArguments(), but array is null
        assertNull(event.getEvaluatedArguments());
    }

    @Test
    void throwableIsPreserved() {
        RuntimeException ex = new RuntimeException("test error");
        LogEvent event = LogEvent.builder()
            .level(Level.ERROR)
            .format("failed")
            .throwable(ex)
            .build();

        assertSame(ex, event.getThrowable());
    }

    @Test
    void mdContextIsPreserved() {
        MDContext ctx = MDContext.builder()
            .add("traceId", "abc")
            .build();

        LogEvent event = LogEvent.builder()
            .level(Level.INFO)
            .format("msg")
            .mdContext(ctx)
            .build();

        assertEquals(ctx, event.getMdContext());
        assertEquals("abc", event.getMdContext().get("traceId"));
    }

    @Test
    void eagerEventSerialization() throws Exception {
        LogEvent original = LogEvent.builder()
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .level(Level.WARN)
            .loggerName("test.Logger")
            .threadName("main")
            .format("count: {}")
            .arguments(new Object[]{42})
            .build();

        LogEvent deserialized = roundTrip(original);

        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertEquals(original.getLevel(), deserialized.getLevel());
        assertEquals(original.getLoggerName(), deserialized.getLoggerName());
        assertEquals(original.getFormat(), deserialized.getFormat());
    }

    @Test
    void equalityAndHashCode() {
        Instant now = Instant.now();
        LogEvent a = LogEvent.builder()
            .timestamp(now).level(Level.INFO).loggerName("test")
            .format("msg").arguments(new Object[]{"x"}).build();
        LogEvent b = LogEvent.builder()
            .timestamp(now).level(Level.INFO).loggerName("test")
            .format("msg").arguments(new Object[]{"x"}).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void allFieldsPopulated() {
        Instant now = Instant.now();
        LogEvent event = LogEvent.builder()
            .timestamp(now)
            .level(Level.DEBUG)
            .loggerName("com.example.Test")
            .threadName("worker-1")
            .format("processing {} items")
            .arguments(new Object[]{100})
            .throwable(new RuntimeException())
            .mdContext(MDContext.empty())
            .build();

        assertEquals(now, event.getTimestamp());
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("com.example.Test", event.getLoggerName());
        assertEquals("worker-1", event.getThreadName());
        assertEquals("processing {} items", event.getFormat());
        assertNotNull(event.getThrowable());
        assertNotNull(event.getMdContext());
    }

    private LogEvent roundTrip(LogEvent event) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(event);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (LogEvent) ois.readObject();
        }
    }
}
