package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.menu.MenuSize;
import de.kyle.virtualinventories.menu.WindowType;
import de.kyle.virtualinventories.serialize.CompiledForm;
import de.kyle.virtualinventories.serialize.MenuCompileException;
import de.kyle.virtualinventories.serialize.Segment;
import de.kyle.virtualinventories.serialize.Segments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime form of a menu, materialized once from a {@link CompiledForm} blob.
 * Static slots hold shared immutable template items, dynamic slots are
 * resolved per open from the viewer's scope.
 */
public final class CompiledMenu {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final String id;
    private final WindowType windowType;
    private final MenuSize size;
    private final Set<Integer> depositSlots;
    private final List<Segment> title;
    private final ItemStack[] staticItems;
    private final List<DynamicSlot> dynamicSlots;
    private final Set<String> placeholderKeys;
    private final Map<Integer, String> actions;

    /** A slot whose content depends on placeholders, resolved per open. */
    public record DynamicSlot(int slot, String material, int amount, String amountPlaceholder,
                              List<Segment> name, List<List<Segment>> lore,
                              List<String> flags, Integer customModelData) {
    }

    private CompiledMenu(String id, WindowType windowType, MenuSize size, Set<Integer> depositSlots,
                         List<Segment> title, ItemStack[] staticItems,
                         List<DynamicSlot> dynamicSlots, Set<String> placeholderKeys,
                         Map<Integer, String> actions) {
        this.id = id;
        this.windowType = windowType;
        this.size = size;
        this.depositSlots = depositSlots;
        this.title = title;
        this.staticItems = staticItems;
        this.dynamicSlots = dynamicSlots;
        this.placeholderKeys = placeholderKeys;
        this.actions = actions;
    }

    /** Builds the runtime menu, pre-building every static slot once. */
    public static CompiledMenu materialize(CompiledForm form) {
        String menuId = form.menuId();
        WindowType windowType;
        try {
            windowType = WindowType.valueOf(form.windowType());
        } catch (IllegalArgumentException e) {
            throw MenuCompileException.at(menuId, "type", "unknown window type '" + form.windowType() + "'");
        }
        MenuSize size = null;
        if (!windowType.isAnvil()) {
            if (form.rows() < 1 || form.rows() > 6) {
                throw MenuCompileException.at(menuId, "rows", "must be 1-6, got " + form.rows());
            }
            size = MenuSize.values()[form.rows() - 1];
        }
        ItemStack[] staticItems = new ItemStack[windowType.slots()];
        List<DynamicSlot> dynamicSlots = new ArrayList<>();
        Map<Integer, String> actions = new LinkedHashMap<>();
        Map<String, String> emptyScope = Map.of();

        for (CompiledForm.CompiledSlot slot : form.slots()) {
            if (slot.slot() < 0 || slot.slot() >= windowType.slots()) {
                throw MenuCompileException.at(menuId, "slots." + slot.slot(), "out of bounds");
            }
            if (slot.action() != null) {
                actions.put(slot.slot(), slot.action());
            }
            if (slot.isDynamic()) {
                dynamicSlots.add(new DynamicSlot(slot.slot(), slot.material(), slot.amount(),
                        slot.amountPlaceholder(), slot.name(), slot.lore(), slot.flags(),
                        slot.customModelData()));
            } else {
                staticItems[slot.slot()] = ItemFactory.build(menuId, slot.slot(), slot.material(),
                        slot.amount(), slot.name(), slot.lore(), slot.flags(),
                        slot.customModelData(), emptyScope);
            }
        }
        return new CompiledMenu(menuId, windowType, size, Set.copyOf(form.depositSlots()),
                form.title(), staticItems,
                List.copyOf(dynamicSlots), form.placeholderKeys(), Map.copyOf(actions));
    }

    public String id() {
        return id;
    }

    public WindowType windowType() {
        return windowType;
    }

    /** Chest grid size, or null for anvil menus. Prefer {@link #slotCount()}. */
    public MenuSize size() {
        return size;
    }

    /** Total top-inventory slots for this window type. */
    public int slotCount() {
        return windowType.slots();
    }

    /** Deposit slots (anvil only): player items live here server-side. */
    public Set<Integer> depositSlots() {
        return depositSlots;
    }

    public boolean isDepositSlot(int slot) {
        return depositSlots.contains(slot);
    }

    public Set<String> placeholderKeys() {
        return placeholderKeys;
    }

    public List<DynamicSlot> dynamicSlots() {
        return dynamicSlots;
    }

    public boolean isStatic() {
        return dynamicSlots.isEmpty() && !Segments.isDynamic(title);
    }

    /** Action id bound to a slot, or null. */
    public String action(int slot) {
        return actions.get(slot);
    }

    public Component renderTitle(Map<String, String> scope) {
        return MINI_MESSAGE.deserialize(Segments.render(title, scope));
    }

    /**
     * Full content array for one open: shared static refs plus freshly
     * resolved dynamic items. The array itself is new per call, static
     * {@link ItemStack}s are shared (never mutate them).
     */
    public ItemStack[] renderStaticView() {
        return staticItems.clone();
    }

    public ItemStack renderDynamic(DynamicSlot slot, Map<String, String> scope) {
        int amount = slot.amount();
        if (slot.amountPlaceholder() != null) {
            try {
                amount = Integer.parseInt(scope.getOrDefault(slot.amountPlaceholder(), "1").strip());
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }
        return ItemFactory.build(id, slot.slot(), slot.material(), amount,
                slot.name(), slot.lore(), slot.flags(), slot.customModelData(), scope);
    }
}
