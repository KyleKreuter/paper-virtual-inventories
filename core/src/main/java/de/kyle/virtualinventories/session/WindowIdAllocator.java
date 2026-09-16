package de.kyle.virtualinventories.session;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocates fake container ids. Id 0 is the player inventory and is never used.
 * Ids are per-player scoped on the client, a shared rolling counter is enough.
 */
public final class WindowIdAllocator {

    private static final int MIN = 1;
    private static final int MAX = 100;

    private final AtomicInteger next = new AtomicInteger(MIN);

    public int allocate() {
        return next.getAndUpdate(current -> current >= MAX ? MIN : current + 1);
    }
}
