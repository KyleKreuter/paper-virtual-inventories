package de.kyle.virtualinventories.menu;

import de.kyle.virtualinventories.session.MenuSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Context of a single click inside a virtual menu.
 * The clicked item is a snapshot copy, mutating it changes nothing server-side.
 *
 * @param player      clicking player
 * @param session     active menu session
 * @param slot        clicked slot within the menu (0..size-1), or -1 for outside/bottom clicks
 * @param rawSlot     raw window slot from the packet (menu slots first, then bottom inventory)
 * @param clickType   simplified click type
 * @param button      raw button id from the packet
 * @param clickedItem snapshot of the displayed item, may be null for empty slots
 * @param bottom      true if the click targeted the player's own (fake-mapped) bottom inventory
 */
public record ClickContext(
        Player player,
        MenuSession session,
        int slot,
        int rawSlot,
        ClickType clickType,
        int button,
        ItemStack clickedItem,
        boolean bottom
) {
    public VirtualMenu menu() {
        return session.menu();
    }
}
