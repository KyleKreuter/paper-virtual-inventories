package de.kyle.virtualinventories.serialize;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializable compiled form of a menu: the exact content of a
 * {@code .vmenu.gz} blob. Texts are pre-parsed {@link Segment}s, so loading
 * never re-parses. DTO-level only (materials as keys, no NMS bytes), which
 * keeps blobs portable across server patch versions.
 */
public record CompiledForm(
        String menuId,
        int rows,
        String windowType,
        List<Integer> depositSlots,
        List<Segment> title,
        List<CompiledSlot> slots,
        Set<String> placeholderKeys,
        String sourceShaHex,
        Map<Integer, Integer> containerData,
        List<Integer> outputSlots,
        Map<Integer, String> buttonActions,
        List<CompiledTrade> trades) {

    public CompiledForm {
        depositSlots = List.copyOf(depositSlots);
        title = List.copyOf(title);
        slots = List.copyOf(slots);
        placeholderKeys = Set.copyOf(placeholderKeys);
        containerData = Map.copyOf(containerData);
        outputSlots = List.copyOf(outputSlots);
        buttonActions = Map.copyOf(buttonActions);
        trades = List.copyOf(trades);
    }

    /** Backwards-compatible constructor for menus without data/output/buttons/trades. */
    public CompiledForm(
            String menuId,
            int rows,
            String windowType,
            List<Integer> depositSlots,
            List<Segment> title,
            List<CompiledSlot> slots,
            Set<String> placeholderKeys,
            String sourceShaHex) {
        this(menuId, rows, windowType, depositSlots, title, slots, placeholderKeys, sourceShaHex,
                Map.of(), List.of(), Map.of(), List.of());
    }

    public boolean isAnvil() {
        return "ANVIL".equals(windowType);
    }

    public boolean isMerchant() {
        return "MERCHANT".equals(windowType);
    }

    public record CompiledSlot(
            int slot,
            String material,
            int amount,
            String amountPlaceholder,
            List<Segment> name,
            List<List<Segment>> lore,
            List<String> flags,
            Integer customModelData,
            String action,
            String itemRef) {

        public CompiledSlot {
            name = List.copyOf(name);
            lore = List.copyOf(lore);
            flags = List.copyOf(flags);
        }

        public boolean isDynamic() {
            return itemRef == null
                    && (amountPlaceholder != null
                    || Segments.isDynamic(name)
                    || lore.stream().anyMatch(Segments::isDynamic));
        }

        /** True for fully serialized item references (resolved at load, never per open). */
        public boolean isReference() {
            return itemRef != null;
        }
    }

    /** One compiled merchant trade (materials as Bukkit keys, template-level). */
    public record CompiledTrade(
            String buyA,
            int buyACount,
            String buyB,
            Integer buyBCount,
            String result,
            int resultCount,
            int maxUses) {
    }
}
