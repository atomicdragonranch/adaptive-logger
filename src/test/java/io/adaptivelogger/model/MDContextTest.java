package io.adaptivelogger.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MDContextTest {

    @AfterEach
    void clearMDC() {
        MDC.clear();
    }

    @Test
    void emptyContextHasNoEntries() {
        MDContext ctx = MDContext.empty();
        assertTrue(ctx.isEmpty());
        assertEquals(0, ctx.size());
    }

    @Test
    void builderCreatesImmutableContext() {
        MDContext ctx = MDContext.builder()
            .add("key1", "val1")
            .add("key2", "val2")
            .build();

        assertEquals(2, ctx.size());
        assertEquals("val1", ctx.get("key1"));
        assertEquals("val2", ctx.get("key2"));
        assertTrue(ctx.containsKey("key1"));
        assertFalse(ctx.containsKey("missing"));
    }

    @Test
    void contextMapIsImmutable() {
        MDContext ctx = MDContext.builder()
            .add("key", "value")
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> ctx.getContextMap().put("new", "entry"));
    }

    @Test
    void nullKeysAndValuesAreIgnored() {
        MDContext ctx = MDContext.builder()
            .add(null, "value")
            .add("key", null)
            .add("valid", "entry")
            .build();

        assertEquals(1, ctx.size());
        assertEquals("entry", ctx.get("valid"));
    }

    @Test
    void currentCapturesMDCState() {
        MDC.put("traceId", "abc-123");
        MDC.put("service", "test");

        MDContext ctx = MDContext.current();
        assertEquals("abc-123", ctx.get("traceId"));
        assertEquals("test", ctx.get("service"));
    }

    @Test
    void currentWithEmptyMDCReturnsEmpty() {
        MDContext ctx = MDContext.current();
        assertTrue(ctx.isEmpty());
    }

    @Test
    void applySetsMDCAndRestores() {
        MDC.put("existing", "value");

        MDContext ctx = MDContext.builder()
            .add("injected", "context")
            .build();

        MDContext.apply(ctx, () -> {
            assertEquals("context", MDC.get("injected"));
            assertEquals("value", MDC.get("existing"));
        });

        assertNull(MDC.get("injected"));
        assertEquals("value", MDC.get("existing"));
    }

    @Test
    void applyWithNullContextJustRunsAction() {
        boolean[] ran = {false};
        MDContext.apply(null, () -> ran[0] = true);
        assertTrue(ran[0]);
    }

    @Test
    void applyWithEmptyContextJustRunsAction() {
        MDC.put("existing", "value");
        MDContext.apply(MDContext.empty(), () -> {
            assertEquals("value", MDC.get("existing"));
        });
    }

    @Test
    void applyRestoresMDCEvenOnException() {
        MDC.put("key", "original");

        MDContext ctx = MDContext.builder()
            .add("key", "overridden")
            .build();

        assertThrows(RuntimeException.class, () ->
            MDContext.apply(ctx, () -> {
                assertEquals("overridden", MDC.get("key"));
                throw new RuntimeException("test");
            })
        );

        assertEquals("original", MDC.get("key"));
    }

    @Test
    void addAllFromMap() {
        MDContext ctx = MDContext.builder()
            .addAll(Map.of("a", "1", "b", "2"))
            .build();

        assertEquals("1", ctx.get("a"));
        assertEquals("2", ctx.get("b"));
    }

    @Test
    void addAllFromContext() {
        MDContext source = MDContext.builder()
            .add("x", "y")
            .build();

        MDContext ctx = MDContext.builder()
            .add("a", "b")
            .addAll(source)
            .build();

        assertEquals("b", ctx.get("a"));
        assertEquals("y", ctx.get("x"));
    }

    @Test
    void removeFromBuilder() {
        MDContext ctx = MDContext.builder()
            .add("keep", "yes")
            .add("drop", "no")
            .remove("drop")
            .build();

        assertEquals(1, ctx.size());
        assertTrue(ctx.containsKey("keep"));
        assertFalse(ctx.containsKey("drop"));
    }

    @Test
    void equalityAndHashCode() {
        MDContext a = MDContext.builder().add("k", "v").build();
        MDContext b = MDContext.builder().add("k", "v").build();
        MDContext c = MDContext.builder().add("k", "other").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void builderWithCurrentIncludesExistingMDC() {
        MDC.put("existing", "value");

        MDContext ctx = MDContext.builderWithCurrent()
            .add("extra", "data")
            .build();

        assertEquals("value", ctx.get("existing"));
        assertEquals("data", ctx.get("extra"));
    }
}
