package de.kyle.virtualinventories.serialize;

import java.util.List;
import java.util.Map;

/**
 * Human-readable menu definition (parsed from YAML or built in code).
 * This is the input of the compiler, never used at open time directly.
 *
 * <p>{@code type} is {@code CHEST} (default, needs {@code rows: 1-6}) or a
 * fixed-size window name like {@code ANVIL}, {@code FURNACE},
 * {@code BREWING_STAND}, {@code MERCHANT}, {@code ENCHANTMENT},
 * {@code STONECUTTER}, {@code LOOM} ({@code rows} is ignored there).
 * {@code deposit} lists player-fillable slots (only on types with
 * {@link de.kyle.virtualinventories.menu.WindowType#allowsDeposit()
 * allowsDeposit}), {@code output} lists take-only slots (result slots),
 * {@code data} holds container-data values (furnace progress, enchantment
 * levels) sent right after opening, {@code buttons} maps
 * {@code ClickWindowButton} ids to action ids, and {@code trades} declares
 * merchant offers.</p>
 */
public record MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots,
                             String type, List<Integer> depositSlots,
                             Map<Integer, Integer> containerData, List<Integer> outputSlots,
                             Map<Integer, String> buttonActions,
                             List<TradeDefinition> trades) {

    public MenuDefinition {
        slots = Map.copyOf(slots);
        type = type == null ? "CHEST" : type.strip().toUpperCase(java.util.Locale.ROOT);
        depositSlots = List.copyOf(depositSlots);
        containerData = Map.copyOf(containerData);
        outputSlots = List.copyOf(outputSlots);
        buttonActions = Map.copyOf(buttonActions);
        trades = List.copyOf(trades);
    }

    /** Backwards-compatible constructor for chest menus without deposit slots. */
    public MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots) {
        this(id, rows, title, slots, "CHEST", List.of(),
                Map.of(), List.of(), Map.of(), List.of());
    }

    /** Constructor for anvil-style menus without container data or trades. */
    public MenuDefinition(String id, int rows, String title, Map<Integer, SlotDefinition> slots,
                          String type, List<Integer> depositSlots) {
        this(id, rows, title, slots, type, depositSlots,
                Map.of(), List.of(), Map.of(), List.of());
    }

    /** One filled slot: visual template plus optional click-action id. */
    public record SlotDefinition(ItemTemplate item, String action) {
    }

    /**
     * One merchant trade. Materials are Bukkit keys; counts default to
     * sensible values when omitted ({@code buy_b: null} = single-cost trade,
     * {@code max_uses} default 12).
     */
    public record TradeDefinition(String buyA, int buyACount, String buyB, Integer buyBCount,
                                  String result, int resultCount, int maxUses) {
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
