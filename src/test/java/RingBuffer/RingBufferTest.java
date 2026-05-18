package RingBuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RingBuffer implementation.
 * Covers: construction, indexOf, Writer, Reader, ReadResult, edge cases.
 */
@DisplayName("RingBuffer Tests")
class RingBufferTest {
    @Test
    void testCapacity() {
        RingBuffer<String> rb = new RingBuffer<>(8);
        assertEquals(8, rb.capacity());
    }

    @Test
    void testCapacity2() {
        // edge case - capacity 1
        RingBuffer<String> rb = new RingBuffer<>(1);
        assertEquals(1, rb.capacity());
    }

    @Test
    void testLastSeq() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        // should be -1 before anything is written
        assertEquals(-1, rb.lastSeq());
    }

    @Test
    void testIndexOf() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        assertEquals(0, rb.indexOf(0));
        assertEquals(0, rb.indexOf(5)); // wraps
        assertEquals(3, rb.indexOf(3));
        assertEquals(4, rb.indexOf(9));   // 9 % 5 = 4
        assertEquals(2, rb.indexOf(12));  // 12 % 5 = 2
    }

    @Test
    void testWriterNotNull() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        assertNotNull(rb.writer());
    }

    @Test
    void testLastSeqAfterWrite() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        Writer<Integer> w = rb.writer();
        w.write(42);
        assertEquals(0, rb.lastSeq());
    }

    @Test
    void testReadEmptyBuffer() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.EMPTY, result.status());
    }

    @Test
    void testReadAfterWrite() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        rb.writer().write(99);
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.OK, result.status());
        assertEquals(Optional.of(99), result.value());
    }

    @Test
    void testReadInOrder() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        w.write(10); w.write(20); w.write(30);
        assertEquals(Optional.of(10), r.read().value());
        assertEquals(Optional.of(20), r.read().value());
        assertEquals(Optional.of(30), r.read().value());
    }

    @Test
    void testSlowReaderGetsMissed() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        // write more than capacity so first item is overwritten
        w.write(1); w.write(2); w.write(3); w.write(4);
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.MISSED, result.status());
    }

    @Test
    void testMissedCount() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        // seqs 0-4, oldest available = 2, reader was at 0, missed = 2
        w.write(0); w.write(1); w.write(2); w.write(3); w.write(4);
        ReadResult<Integer> result = r.read();
        assertEquals(2, result.missedCount());
    }

    @Test
    void testAfterMissedReaderContinues() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        w.write(0); w.write(1); w.write(2); w.write(3); w.write(4);
        r.read(); // MISSED - advances to seq 2
        ReadResult<Integer> next = r.read();
        assertEquals(ReadResult.Status.OK, next.status());
        assertEquals(Optional.of(2), next.value());
    }

    @Test
    void testTwoReadersAreIndependent() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r1 = rb.createReader();
        Reader<Integer> r2 = rb.createReader();
        Writer<Integer> w = rb.writer();
        w.write(100); w.write(200);
        assertEquals(Optional.of(100), r1.read().value());
        // r2 should still be at the beginning
        assertEquals(Optional.of(100), r2.read().value());
        assertEquals(Optional.of(200), r2.read().value());
    }

    @Test
    void testLateReaderSeesOnlyNewItems() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Writer<Integer> w = rb.writer();
        w.write(1); w.write(2);
        Reader<Integer> lateReader = rb.createReader();
        // should be empty - nothing written after reader creation
        assertEquals(ReadResult.Status.EMPTY, lateReader.read().status());
        w.write(3);
        assertEquals(Optional.of(3), lateReader.read().value());
    }

    @Test
    void testReadResultOk() {
        ReadResult<String> r = ReadResult.ok("hello");
        assertEquals(ReadResult.Status.OK, r.status());
        assertEquals(Optional.of("hello"), r.value());
        assertEquals(0, r.missedCount());
    }

    @Test
    void testReadResultEmpty() {
        ReadResult<String> r = ReadResult.empty();
        assertEquals(ReadResult.Status.EMPTY, r.status());
        assertTrue(r.value().isEmpty());
        assertEquals(0, r.missedCount());
    }

    @Test
    void testReadResultMissed() {
        ReadResult<String> r = ReadResult.missed(5);
        assertEquals(ReadResult.Status.MISSED, r.status());
        assertTrue(r.value().isEmpty());
        assertEquals(5, r.missedCount());
    }

    @Test
    void testWritingBeyondCapacityDoesNotThrow() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Writer<Integer> w = rb.writer();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) w.write(i);
        });
    }

    @Test
    void testRepeatedReadsOnEmptyReturnEmpty() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        rb.writer().write(1);
        r.read(); // OK
        // all subsequent reads should be EMPTY
        for (int i = 0; i < 5; i++) {
            assertEquals(ReadResult.Status.EMPTY, r.read().status());
        }
    }
}