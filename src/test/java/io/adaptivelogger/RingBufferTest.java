package io.adaptivelogger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void newBufferIsEmpty() {
        RingBuffer<String> buffer = new RingBuffer<>(10);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(0, buffer.size());
        assertEquals(10, buffer.capacity());
    }

    @Test
    void addAndRetrieveElements() {
        RingBuffer<String> buffer = new RingBuffer<>(5);
        buffer.add("a");
        buffer.add("b");
        buffer.add("c");

        assertEquals(3, buffer.size());
        assertFalse(buffer.isFull());
        assertEquals(List.of("a", "b", "c"), buffer.getAll());
    }

    @Test
    void overflowOverwritesOldest() {
        RingBuffer<Integer> buffer = new RingBuffer<>(3);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        assertTrue(buffer.isFull());

        buffer.add(4);
        assertEquals(3, buffer.size());

        List<Integer> elements = buffer.getAll();
        assertEquals(List.of(2, 3, 4), elements);
    }

    @Test
    void multipleOverflowCycles() {
        RingBuffer<Integer> buffer = new RingBuffer<>(3);
        for (int i = 1; i <= 10; i++) {
            buffer.add(i);
        }

        assertEquals(3, buffer.size());
        assertEquals(List.of(8, 9, 10), buffer.getAll());
    }

    @Test
    void nullElementsAreIgnored() {
        RingBuffer<String> buffer = new RingBuffer<>(5);
        buffer.add("a");
        buffer.add(null);
        buffer.add("b");

        assertEquals(2, buffer.size());
        assertEquals(List.of("a", "b"), buffer.getAll());
    }

    @Test
    void clearResetsBuffer() {
        RingBuffer<String> buffer = new RingBuffer<>(5);
        buffer.add("a");
        buffer.add("b");

        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.getAll());
    }

    @Test
    void forEachVisitsAllElements() {
        RingBuffer<String> buffer = new RingBuffer<>(5);
        buffer.add("a");
        buffer.add("b");
        buffer.add("c");

        List<String> visited = new ArrayList<>();
        buffer.forEach(visited::add);
        assertEquals(List.of("a", "b", "c"), visited);
    }

    @Test
    void forEachAfterOverflow() {
        RingBuffer<Integer> buffer = new RingBuffer<>(3);
        for (int i = 1; i <= 5; i++) {
            buffer.add(i);
        }

        List<Integer> visited = new ArrayList<>();
        buffer.forEach(visited::add);
        assertEquals(List.of(3, 4, 5), visited);
    }

    @Test
    void snapshotCapturesState() {
        RingBuffer<String> buffer = new RingBuffer<>(10);
        buffer.add("a");
        buffer.add("b");

        RingBuffer.BufferSnapshot<String> snapshot = buffer.getSnapshot();
        assertEquals(2, snapshot.getSize());
        assertEquals(10, snapshot.getCapacity());
        assertEquals(0.2, snapshot.getUtilization(), 0.001);
        assertEquals(List.of("a", "b"), snapshot.getElements());
    }

    @Test
    void capacityOfOneWorks() {
        RingBuffer<String> buffer = new RingBuffer<>(1);
        buffer.add("a");
        assertTrue(buffer.isFull());
        assertEquals(List.of("a"), buffer.getAll());

        buffer.add("b");
        assertEquals(List.of("b"), buffer.getAll());
    }

    @Test
    void invalidCapacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(-1));
    }

    @Test
    void concurrentAddsDoNotCorrupt() throws Exception {
        RingBuffer<Integer> buffer = new RingBuffer<>(100);
        int threadCount = 8;
        int addsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < addsPerThread; i++) {
                    buffer.add(threadId * addsPerThread + i);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(100, buffer.size());
        List<Integer> elements = buffer.getAll();
        assertEquals(100, elements.size());
        elements.forEach(e -> assertNotNull(e));
    }
}
