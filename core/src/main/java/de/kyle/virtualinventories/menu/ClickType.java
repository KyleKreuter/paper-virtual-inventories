package de.kyle.virtualinventories.menu;

/**
 * Simplified click types derived from the client's window-click packet.
 * Mapped defensively by name so unknown future modes fall back to UNKNOWN.
 */
public enum ClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    NUMBER_KEY,
    DROP,
    DRAG,
    DOUBLE_CLICK,
    UNKNOWN;

    /**
     * Maps a PacketEvents {@code WindowClickType} (by name) to this enum.
     *
     * @param packetTypeName value of {@code WindowClickType.name()}
     * @param button         mouse button / key from the click packet
     */
    public static ClickType fromPacket(String packetTypeName, int button) {
        return switch (packetTypeName) {
            case "PICKUP" -> button == 1 ? RIGHT : LEFT;
            case "QUICK_MOVE" -> button == 1 ? SHIFT_RIGHT : SHIFT_LEFT;
            case "SWAP" -> NUMBER_KEY;
            case "CLONE" -> MIDDLE;
            case "THROW" -> DROP;
            case "QUICK_CRAFT" -> DRAG;
            case "PICKUP_ALL" -> DOUBLE_CLICK;
            default -> UNKNOWN;
        };
    }
}
