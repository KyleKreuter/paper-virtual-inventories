package de.kyle.virtualinventories.serialize;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and renders placeholder-aware texts.
 *
 * <p>Syntax: {@code %key%} references placeholder {@code key}
 * ({@code [A-Za-z0-9_]+}). {@code %%} is a literal percent sign, a lone
 * {@code %} or an invalid key is kept as literal text.</p>
 *
 * <p>Pure logic, no Bukkit dependency (unit-testable).</p>
 */
public final class Segments {

    private Segments() {
    }

    public static List<Segment> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Segment> out = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c != '%') {
                literal.append(c);
                i++;
                continue;
            }
            if (i + 1 < n && raw.charAt(i + 1) == '%') {
                literal.append('%');
                i += 2;
                continue;
            }
            int end = raw.indexOf('%', i + 1);
            if (end < 0) {
                literal.append(raw, i, n);
                break;
            }
            String key = raw.substring(i + 1, end);
            if (isKey(key)) {
                flush(literal, out);
                out.add(new Segment.Placeholder(key));
                i = end + 1;
            } else {
                literal.append('%');
                i++;
            }
        }
        flush(literal, out);
        return List.copyOf(out);
    }

    public static boolean isKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean ok = c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static boolean isDynamic(List<Segment> segments) {
        for (Segment segment : segments) {
            if (segment.isDynamic()) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> keys(List<Segment> segments) {
        Set<String> keys = new LinkedHashSet<>();
        for (Segment segment : segments) {
            if (segment instanceof Segment.Placeholder placeholder) {
                keys.add(placeholder.key());
            }
        }
        return keys;
    }

    /**
     * Renders segments with the given scope. Missing keys render as an empty
     * string. Values are escaped so they can never inject MiniMessage tags.
     */
    public static String render(List<Segment> segments, Map<String, String> scope) {
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) {
            if (segment instanceof Segment.Placeholder placeholder) {
                out.append(escape(scope.getOrDefault(placeholder.key(), "")));
            } else if (segment instanceof Segment.Literal literal) {
                out.append(literal.text());
            }
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static void flush(StringBuilder literal, List<Segment> out) {
        if (!literal.isEmpty()) {
            out.add(new Segment.Literal(literal.toString()));
            literal.setLength(0);
        }
    }
}
