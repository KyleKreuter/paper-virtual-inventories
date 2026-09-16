package de.kyle.virtualinventories.serialize;

import java.util.List;
import java.util.Map;

/**
 * Human-readable menu definition (parsed from YAML or built in code).
 * This is the input of the compiler, never used at open time directly.
 */
public record MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots) {

    public MenuDefinition {
        slots = Map.copyOf(slots);
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
