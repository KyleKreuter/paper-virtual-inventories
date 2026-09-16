package de.kyle.virtualinventories.menu;

/**
 * Chest sizes supported by virtual menus. The window type id matches the vanilla
 * {@code MenuType} registry order on 1.21 (GENERIC_9x1 = 0 .. GENERIC_9x6 = 5).
 */
public enum MenuSize {
    ROW_1(9),
    ROW_2(18),
    ROW_3(27),
    ROW_4(36),
    ROW_5(45),
    ROW_6(54);

    private final int slots;

    MenuSize(int slots) {
        this.slots = slots;
    }

    public int slots() {
        return slots;
    }

    public int rows() {
        return slots / 9;
    }

    /** Vanilla window type id for {@code WrapperPlayServerOpenWindow}. */
    public int windowTypeId() {
        return ordinal();
    }

    public static MenuSize forSlots(int slots) {
        for (MenuSize size : values()) {
            if (size.slots >= slots) {
                return size;
            }
        }
        return ROW_6;
    }
}
