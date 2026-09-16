package de.kyle.virtualinventories.serialize;

import java.util.List;
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
        String sourceShaHex) {

    public CompiledForm {
        depositSlots = List.copyOf(depositSlots);
        title = List.copyOf(title);
        slots = List.copyOf(slots);
        placeholderKeys = Set.copyOf(placeholderKeys);
    }

    public boolean isAnvil() {
        return "ANVIL".equals(windowType);
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
            String action) {

        public CompiledSlot {
            name = List.copyOf(name);
            lore = List.copyOf(lore);
            flags = List.copyOf(flags);
        }

        public boolean isDynamic() {
            return amountPlaceholder != null
                    || Segments.isDynamic(name)
                    || lore.stream().anyMatch(Segments::isDynamic);
        }
    }
}
