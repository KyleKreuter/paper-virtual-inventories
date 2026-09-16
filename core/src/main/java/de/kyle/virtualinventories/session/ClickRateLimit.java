package de.kyle.virtualinventories.session;

/**
 * Click rate limit for virtual menus: a token bucket per open session.
 *
 * <p>Every click packet costs one token. The bucket holds at most
 * {@code maxBurst} tokens and refills at {@code perSecond} tokens per second.
 * Clicks arriving with an empty bucket are silently dropped (the client-side
 * ghost is healed by a throttled resync instead of running the click action).
 * This bounds what a modded client or macro can trigger: at most
 * {@code maxBurst} actions in a burst, {@code perSecond} sustained.</p>
 *
 * <p>Configured once in {@code VirtualInventories.init(plugin, limit)} and
 * applied to every session. Retargeting (in-place navigation) keeps the same
 * session — and therefore the same bucket — so switching menus cannot be
 * used to dodge the limit.</p>
 *
 * @param maxBurst  bucket capacity: how many clicks are allowed at once
 * @param perSecond sustained click rate: refill speed in tokens per second
 */
public record ClickRateLimit(int maxBurst, int perSecond) {

    public ClickRateLimit {
        if (maxBurst < 1) {
            throw new IllegalArgumentException("maxBurst must be >= 1, was " + maxBurst);
        }
        if (perSecond < 1) {
            throw new IllegalArgumentException("perSecond must be >= 1, was " + perSecond);
        }
    }

    /**
     * Sensible default: bursts of 10, 10 clicks per second sustained.
     * Far above legitimate play (fast clicking is ~5/s), far below spam.
     */
    public static ClickRateLimit defaults() {
        return new ClickRateLimit(10, 10);
    }
}
