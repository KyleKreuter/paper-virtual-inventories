package de.kyle.virtualinventories.menu;

import org.bukkit.inventory.ItemStack;

/**
 * A single slot of a virtual menu: a purely visual icon plus an optional click handler.
 * The icon is never given to the player, it is only rendered client-side via packets.
 */
public record MenuItem(ItemStack icon, ClickHandler handler) {

    public static MenuItem just(ItemStack icon) {
        return new MenuItem(icon, null);
    }

    public static MenuItem of(ItemStack icon, ClickHandler handler) {
        return new MenuItem(icon, handler);
    }

    public boolean hasHandler() {
        return handler != null;
    }

    public MenuItem withIcon(ItemStack icon) {
        return new MenuItem(icon, handler);
    }
}
