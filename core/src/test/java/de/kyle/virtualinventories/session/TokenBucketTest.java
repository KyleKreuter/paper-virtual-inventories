package de.kyle.virtualinventories.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    @Test
    void burstAllowsCapacityThenDenies() {
        TokenBucket bucket = new TokenBucket(3, 1, new AtomicLong()::get);
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());
    }

    @Test
    void refillOverTime() {
        AtomicLong clock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(2, 2, clock::get);
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());

        clock.addAndGet(1_000_000_000L); // +1s at 2/s refills 2 tokens
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());
    }

    @Test
    void refillIsCappedAtCapacity() {
        AtomicLong clock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(2, 100, clock::get);
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());

        clock.addAndGet(3_600_000_000_000L); // +1h must not overfill
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());
    }

    @Test
    void clockGoingBackwardsNeverCreatesTokens() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        TokenBucket bucket = new TokenBucket(1, 1, clock::get);
        assertTrue(bucket.tryConsume());

        clock.set(0); // clock skew: no refill, still empty
        assertFalse(bucket.tryConsume());
    }

    @Test
    void rateLimitRejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class, () -> new ClickRateLimit(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ClickRateLimit(10, 0));
    }

    @Test
    void defaultsAreSane() {
        ClickRateLimit defaults = ClickRateLimit.defaults();
        assertEquals(10, defaults.maxBurst());
        assertEquals(10, defaults.perSecond());
    }
}
