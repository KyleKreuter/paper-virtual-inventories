package de.kyle.virtualinventories.serialize;

import java.util.List;
import java.util.Map;

/**
 * Human-readable menu definition (parsed from YAML or built in code).
 * This is the input of the compiler, never used at open time directly.
 *
 * <p>{@code type} is {@code CHEST} (default, needs {@code rows: 1-6}) or
 * {@code ANVIL} (fixed 3-slot window, {@code rows} is ignored). {@code deposit}
 * lists player-fillable slots; only meaningful for anvils (defaults to
 * {@code [0, 1]} there, must be empty for chests).</p>
 */
public record MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots,
                             String type, List<Integer> depositSlots) {

    public MenuDefinition {
        slots = Map.copyOf(slots);
        type = type == null ? "CHEST" : type.strip().toUpperCase(java.util.Locale.ROOT);
        depositSlots = List.copyOf(depositSlots);
    }

    /** Backwards-compatible constructor for chest menus without deposit slots. */
    public MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots) {
        this(id, rows, title, slots, "CHEST", List.of());
    }

    /** One filled slot: visual template plus optional click-action id. */
    public record SlotDefinition(ItemTemplate item, String action) {
    }

    /**
     * Visual blueprint of one item. Texts are MiniMessage strings that may
     * contain {@code %placeholder%} references.
     */
    public record ItemTemplate(
            String material,
            int amount,
            String amountPlaceholder,
            String name,
            List<String> lore,
            List<String> flags,
            Integer customModelData) {

        public ItemTemplate {
            lore = List.copyOf(lore);
            flags = List.copyOf(flags);
        }

        public static ItemTemplate simple(String material, String name) {
            return new ItemTemplate(material, 1, null, name, List.of(), List.of(), null);
        }
    }
}
