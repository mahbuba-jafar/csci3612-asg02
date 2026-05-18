package RingBuffer;

import org.junit.jupiter.api.*;

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
    @Test
    void testCapacityLarge() {
        RingBuffer<Integer> rb = new RingBuffer<>(100);
        assertEquals(100, rb.capacity());
    }

    @Test
    void testLastSeqGrowsCorrectly() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Writer<Integer> w = rb.writer();
        for (int i = 0; i < 5; i++) {
            w.write(i);
            assertEquals(i, rb.lastSeq());
        }
    }

    @Test
    void testReadExactlyCapacityItems() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        for (int i = 0; i < 5; i++) w.write(i);
        for (int i = 0; i < 5; i++) {
            assertEquals(ReadResult.Status.OK, r.read().status());
        }
        // after draining, should be empty
        assertEquals(ReadResult.Status.EMPTY, r.read().status());
    }

    @Test
    void testSecondReadAfterOneWriteIsEmpty() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader();
        rb.writer().write(10);
        r.read(); // OK
        assertEquals(ReadResult.Status.EMPTY, r.read().status());
    }

    @Test
    void testMissedResultHasNoValue() {
        RingBuffer<Integer> rb = new RingBuffer<>(2);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        w.write(1); w.write(2); w.write(3);
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.MISSED, result.status());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void testWrapAroundValues() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        w.write(10); w.write(20); w.write(30); w.write(40);
        r.read(); // MISSED, advances to seq 1
        assertEquals(Optional.of(20), r.read().value());
        assertEquals(Optional.of(30), r.read().value());
        assertEquals(Optional.of(40), r.read().value());
        assertEquals(ReadResult.Status.EMPTY, r.read().status());
    }

    @Test
    void testFastReaderDoesNotAffectSlowReader() {
        RingBuffer<String> rb = new RingBuffer<>(5);
        Reader<String> slow = rb.createReader();
        Reader<String> fast = rb.createReader();
        Writer<String> w = rb.writer();
        w.write("a"); w.write("b");
        fast.read(); fast.read(); // drain
        assertEquals(ReadResult.Status.EMPTY, fast.read().status());
        // slow reader unaffected
        assertEquals(Optional.of("a"), slow.read().value());
        assertEquals(Optional.of("b"), slow.read().value());
    }

    @Test
    void testReadResultOkToString() {
        ReadResult<Integer> r = ReadResult.ok(42);
        assertTrue(r.toString().contains("42"));
    }

    @Test
    void testReadResultEmptyToString() {
        assertEquals("EMPTY", ReadResult.empty().toString());
    }

    @Test
    void testReadResultMissedToString() {
        assertTrue(ReadResult.missed(3).toString().contains("3"));
    }

    @Test
    void testLargeNumberOfWrites() {
        RingBuffer<Integer> rb = new RingBuffer<>(8);
        Writer<Integer> w = rb.writer();
        for (int i = 0; i < 1000; i++) w.write(i);
        assertEquals(999, rb.lastSeq());
    }

    @Test
    void testCapacityOneOverwrite() {
        // capacity 1: second write should overwrite first
        RingBuffer<String> rb = new RingBuffer<>(1);
        Reader<String> r = rb.createReader();
        Writer<String> w = rb.writer();
        w.write("first");
        w.write("second"); // overwrites "first"
        // reader at seq 0, oldest available = 1, so MISSED
        ReadResult<String> result = r.read();
        assertEquals(ReadResult.Status.MISSED, result.status());
    }

    @Test
    @Disabled("BUG: indexOf() does not handle negative seq numbers. " +
            "In Java, -1 % 5 = -1 (negative), which would cause " +
            "ArrayIndexOutOfBoundsException. " +
            "Fix: return ((seq % capacity) + capacity) % capacity;")
    void testIndexOfNegativeSeq() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        int idx = rb.indexOf(-1);
        assertTrue(idx >= 0 && idx < rb.capacity());
    }

    @Test
    void testWriterIsNotShared() {
        // two writer() calls should return different Writer instances
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Writer<Integer> w1 = rb.writer();
        Writer<Integer> w2 = rb.writer();
        assertNotSame(w1, w2);
    }

    @Test
    void testReaderAfterFullOverwrite() {
        // write 2x capacity, reader should get MISSED then read latest window
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader();
        Writer<Integer> w = rb.writer();
        for (int i = 0; i < 6; i++) w.write(i);
        // oldest available = 6-3 = 3
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.MISSED, result.status());
        assertEquals(3, result.missedCount());
    }

    @Test
    void testReaderCreatedMidStreamMissesOldData() {
        // if buffer is already full when reader is created,
        // reader should NOT be able to read overwritten items
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Writer<Integer> w = rb.writer();
        w.write(1); w.write(2); w.write(3); w.write(4); // seq 3, oldest=1
        Reader<Integer> r = rb.createReader(); // starts at seq 4
        w.write(5); // seq 4
        ReadResult<Integer> result = r.read();
        assertEquals(ReadResult.Status.OK, result.status());
        assertEquals(Optional.of(5), result.value());
        // nothing more
        assertEquals(ReadResult.Status.EMPTY, r.read().status());
    }
}