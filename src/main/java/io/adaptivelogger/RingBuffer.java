package io.adaptivelogger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * A thread-safe ring buffer implementation for storing log events.
 * Uses atomic operations and read-write locks for efficient concurrent access.
 *
 * @param <T> The type of elements stored in the buffer
 */
public class RingBuffer<T> {
    private final Object[] buffer;
    private final int capacity;
    private final AtomicInteger writeIndex;
    private final AtomicInteger size;
    private final ReentrantReadWriteLock lock;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }

        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.writeIndex = new AtomicInteger(0);
        this.size = new AtomicInteger(0);
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Adds an element to the ring buffer.
     * If the buffer is full, the oldest element is overwritten.
     *
     * @param element The element to add
     */
    public void add(T element) {
        if (element == null) {
            return;
        }

        // Get the next write position:
        int index = writeIndex.getAndIncrement() % capacity;

        // Write the element:
        buffer[index] = element;

        // Update size atomically, capping at capacity:
        // updateAndGet() prevents race conditions by atomically reading the current value,
        // applying the function, and updating the value in a single operation.
        // If another thread modifies size between read and update, it retries automatically:
        size.updateAndGet(current -> Math.min(current + 1, capacity));
    }

    /**
     * Retrieves all elements currently in the buffer in insertion order.
     *
     * @return A list of all elements in the buffer
     */
    @SuppressWarnings("unchecked")
    public List<T> getAll() {
        lock.readLock().lock();
        try {
            List<T> result = new ArrayList<>(size.get());
            int currentSize = size.get();
            int currentWriteIndex = writeIndex.get();

            if (currentSize == 0) {
                return result;
            }

            // Calculate start position:
            int startIndex;
            if (currentSize < capacity) {
                // Buffer not full yet, start from 0:
                startIndex = 0;
            } else {
                // Buffer is full, start from oldest element:
                startIndex = currentWriteIndex % capacity;
            }

            // Copy elements in order:
            for (int i = 0; i < currentSize; i++) {
                int index = (startIndex + i) % capacity;
                T element = (T) buffer[index];
                if (element != null) {
                    result.add(element);
                }
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Applies the given consumer to each element in the buffer in insertion order.
     *
     * @param consumer The consumer to apply to each element
     */
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<T> consumer) {
        lock.readLock().lock();
        try {
            int currentSize = size.get();
            int currentWriteIndex = writeIndex.get();

            if (currentSize == 0) {
                return;
            }

            // Calculate start position:
            int startIndex;
            if (currentSize < capacity) {
                startIndex = 0;
            } else {
                startIndex = currentWriteIndex % capacity;
            }

            // Process elements in order:
            for (int i = 0; i < currentSize; i++) {
                int index = (startIndex + i) % capacity;
                T element = (T) buffer[index];
                if (element != null) {
                    consumer.accept(element);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current number of elements in the buffer.
     *
     * @return The number of elements
     */
    public int size() {
        return size.get();
    }

    /**
     * Returns the maximum capacity of the buffer.
     *
     * @return The capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Checks if the buffer is empty.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return size.get() == 0;
    }

    /**
     * Checks if the buffer is full.
     *
     * @return true if full, false otherwise
     */
    public boolean isFull() {
        return size.get() >= capacity;
    }

    /**
     * Clears all elements from the buffer.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            // Clear all references to help GC:
            for (int i = 0; i < capacity; i++) {
                buffer[i] = null;
            }
            writeIndex.set(0);
            size.set(0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets a snapshot of the buffer's current state.
     * Useful for debugging and monitoring.
     *
     * @return A snapshot of the buffer state
     */
    public BufferSnapshot<T> getSnapshot() {
        lock.readLock().lock();
        try {
            return new BufferSnapshot<>(
                size.get(),
                capacity,
                writeIndex.get(),
                getAll()
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * A snapshot of the ring buffer's state at a point in time.
     */
    public static class BufferSnapshot<T> {
        private final int size;
        private final int capacity;
        private final int writeIndex;
        private final List<T> elements;

        public BufferSnapshot(int size, int capacity, int writeIndex, List<T> elements) {
            this.size = size;
            this.capacity = capacity;
            this.writeIndex = writeIndex;
            this.elements = elements;
        }

        public int getSize() {
            return size;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getWriteIndex() {
            return writeIndex;
        }

        public List<T> getElements() {
            return elements;
        }

        public double getUtilization() {
            return (double) size / capacity;
        }
    }
}
