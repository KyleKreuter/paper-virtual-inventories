package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.session.MenuSession;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

/**
 * Programmatic open/close/deposit hooks per menu (not serialized, registered
 * in code). Hooks are plumbing: they observe, game rules live in handlers.
 */
public record MenuHooks(BiConsumer<Player, MenuSession> onOpen, BiConsumer<Player, MenuSession> onClose,
                        DepositHook onDeposit) {

    /**
     * Fires after a deposit transfer changed server-side content. The slot is
     * the menu-grid deposit slot, or -1 for inventory-side (shift-click)
     * changes. Use it to update previews (merchant results, ...).
     */
    @FunctionalInterface
    public interface DepositHook {
        void depositChanged(Player player, MenuSession session, int slot);
    }

    public static MenuHooks empty() {
        return new MenuHooks(null, null, null);
    }
}
