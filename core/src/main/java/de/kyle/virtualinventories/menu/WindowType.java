package de.kyle.virtualinventories.menu;

/**
 * Window types supported by virtual menus. The vanilla type id follows the
 * {@code MenuType} registry order on 1.21 (GENERIC_9x1 = 0 .. GENERIC_9x6 = 5,
 * GENERIC_3x3 = 6, CRAFTER_3x3 = 7, ANVIL = 8, BEACON = 9, BLAST_FURNACE = 10,
 * BREWING_STAND = 11, ENCHANTMENT = 13, FURNACE = 14, GRINDSTONE = 15,
 * HOPPER = 16, LECTERN = 17, LOOM = 18, MERCHANT = 19, SHULKER_BOX = 20,
 * SMITHING = 21, SMOKER = 22, CARTOGRAPHY_TABLE = 23, STONECUTTER = 24).
 * (CRAFTING = 12 is the player inventory crafting grid, not a block window.)
 *
 * <p>Plain containers render every slot from the compiled template. Furnaces,
 * brewing stands, merchants, enchantment tables, stonecutters and looms need
 * extra server data (container data values, offers, button actions) supplied
 * via the matching definition sections.</p>
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
    SHULKER_BOX(20, 27),
    FURNACE(14, 3),
    BLAST_FURNACE(10, 3),
    SMOKER(22, 3),
    BREWING_STAND(11, 5),
    MERCHANT(19, 3),
    ENCHANTMENT(13, 2),
    STONECUTTER(24, 2),
    LOOM(18, 4),
    BEACON(9, 1),
    GRINDSTONE(15, 3),
    LECTERN(17, 1),
    SMITHING(21, 4),
    CARTOGRAPHY_TABLE(23, 3);

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

    /**
     * Whether players may put their own items into {@code deposit} slots of
     * this window: anvils, furnaces, brewing stands, enchantment tables,
     * merchant input slots, stonecutter and loom input slots, beacon payment,
     * grindstone, lectern, smithing and cartography inputs. Output
     * slots ({@code output}) are take-only and allowed wherever they are
     * declared.
      */
    public boolean allowsDeposit() {
        return switch (this) {
            case ANVIL, FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND,
                    ENCHANTMENT, MERCHANT, STONECUTTER, LOOM,
                    BEACON, GRINDSTONE, LECTERN, SMITHING, CARTOGRAPHY_TABLE -> true;
            default -> false;
        };
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
