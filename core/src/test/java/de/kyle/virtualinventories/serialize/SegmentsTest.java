package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentsTest {

    @Test
    void plainTextIsSingleLiteral() {
        assertEquals(List.of(new Segment.Literal("hello")), Segments.parse("hello"));
    }

    @Test
    void placeholderIsSplitOut() {
        assertEquals(
                List.of(new Segment.Literal("Hi "), new Segment.Placeholder("player"), new Segment.Literal("!")),
                Segments.parse("Hi %player%!"));
    }

    @Test
    void doublePercentIsEscaped() {
        assertEquals(List.of(new Segment.Literal("100%")), Segments.parse("100%%"));
    }

    @Test
    void lonePercentStaysLiteral() {
        assertEquals(List.of(new Segment.Literal("lone % here")), Segments.parse("lone % here"));
    }

    @Test
    void invalidKeyStaysLiteral() {
        assertEquals(List.of(new Segment.Literal("%bad-key%")), Segments.parse("%bad-key%"));
    }

    @Test
    void keysAreCollectedInOrder() {
        List<Segment> segments = Segments.parse("%b% x %a% %b%");
        assertEquals(List.of("b", "a"), List.copyOf(Segments.keys(segments)));
    }

    @Test
    void dynamicDetection() {
        assertFalse(Segments.isDynamic(Segments.parse("plain")));
        assertTrue(Segments.isDynamic(Segments.parse("hi %player%")));
    }

    @Test
    void renderSubstitutesAndDropsMissing() {
        List<Segment> segments = Segments.parse("Hi %player%%missing%!");
        assertEquals("Hi Steve!", Segments.render(segments, Map.of("player", "Steve")));
    }

    @Test
    void renderEscapesValues() {
        List<Segment> segments = Segments.parse("[%name%]");
        assertEquals("[a\\<b]", Segments.render(segments, Map.of("name", "a<b")));
    }
}
