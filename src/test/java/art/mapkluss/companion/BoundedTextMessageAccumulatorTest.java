package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BoundedTextMessageAccumulatorTest {
    @Test
    void acceptsFragmentedMessageAtLimit() {
        BoundedTextMessageAccumulator accumulator = new BoundedTextMessageAccumulator(5);
        assertFalse(accumulator.append("ab", false).complete());
        BoundedTextMessageAccumulator.Result result = accumulator.append("cde", true);
        assertEquals("abcde", result.message());
        assertFalse(result.overflow());
    }

    @Test
    void rejectsOverflowAndDiscardsRemainingFragments() {
        BoundedTextMessageAccumulator accumulator = new BoundedTextMessageAccumulator(4);
        accumulator.append("abc", false);
        assertTrue(accumulator.append("de", false).overflow());
        assertNull(accumulator.append("ignored", true).message());
        assertEquals("ok", accumulator.append("ok", true).message());
    }

    @Test
    void resetDropsPartialMessage() {
        BoundedTextMessageAccumulator accumulator = new BoundedTextMessageAccumulator(10);
        accumulator.append("old", false);
        accumulator.reset();
        assertEquals("new", accumulator.append("new", true).message());
    }
}
