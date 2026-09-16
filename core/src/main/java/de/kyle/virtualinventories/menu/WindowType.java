package de.kyle.virtualinventories.menu;

/**
 * Window types supported by virtual menus. The vanilla type id follows the
 * {@code MenuType} registry order on 1.21 (GENERIC_9x1 = 0 .. GENERIC_9x6 = 5,
 * GENERIC_3x3 = 6, CRAFTER_3x3 = 7, ANVIL = 8, HOPPER = 16, SHULKER_BOX = 20).
 *
 * <p>Only plain container windows are supported: every slot is either static
 * or an action slot. Types that need extra server data (furnace progress,
 * enchantment options, merchant offers, stonecutter recipes, beacon levels)
 * are intentionally not included.
 */
public enum WindowType {
    CHEST_9X1(0, 9),
    CHEST_9X2(1, 18),
    CHEST_9X3(2, 27),
    CHEST_9X4(3, 36),
    CHEST_9X5(4, 45),
    CHEST_9X6(5, 54),
    ANVIL(8, 3),
    GENERIC_3X3(6, 9),
    CRAFTER_3X3(7, 9),
    HOPPER(16, 5),
    SHULKER_BOX(20, 27);

    private final int typeId;
    private final int slots;

    WindowType(int typeId, int slots) {
        this.typeId = typeId;
        this.slots = slots;
    }

    /** Vanilla window type id for {@code WrapperPlayServerOpenWindow}. */
    public int typeId() {
        return typeId;
    }

    public int slots() {
        return slots;
    }

    public boolean isAnvil() {
        return this == ANVIL;
    }

    /** Chest window for a row count (1-6). */
    public static WindowType chest(MenuSize size) {
        return values()[size.ordinal()];
    }

    /** Chest window for a row count (1-6). */
    public static WindowType chest(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1-6, got " + rows);
        }
        return values()[rows - 1];
    }
}
