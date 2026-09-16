package de.kyle.virtualinventories.serialize;

/**
 * One piece of a placeholder-aware text: either literal text or a
 * {@code %key%} reference resolved per menu open.
 */
public sealed interface Segment permits Segment.Literal, Segment.Placeholder {

    /** Plain text, sent as-is (MiniMessage tags inside are evaluated). */
    record Literal(String text) implements Segment {
    }

    /** Reference to a placeholder key, resolved from the open scope. */
    record Placeholder(String key) implements Segment {
    }

    default boolean isDynamic() {
        return this instanceof Placeholder;
    }
}
