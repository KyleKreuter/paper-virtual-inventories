package de.kyle.virtualinventories.session;

import java.util.function.LongSupplier;

/**
 * Token bucket for click spam protection. One token per click, refill over
 * time, capped at capacity.
 *
 * <p>Synchronized: the gate runs on the Netty thread while sessions live on
 * the main thread. The clock is injectable so unit tests stay deterministic.</p>
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastNanos;

    TokenBucket(int capacity, int refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    TokenBucket(int capacity, int refillPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastNanos = nanoClock.getAsLong();
    }

    /** Consumes one token if available. */
    synchronized boolean tryConsume() {
        long now = nanoClock.getAsLong();
        double elapsedSeconds = Math.max(0, now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
