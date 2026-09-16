package de.kyle.virtualinventories.session;

import de.kyle.virtualinventories.menu.MenuItem;
import de.kyle.virtualinventories.menu.VirtualMenu;
import de.kyle.virtualinventories.net.MenuPacketSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side state of one open virtual menu. Holds no real items, only the
 * last rendered visual content plus the fake window id.
 *
 * <p>All methods must be called on the Bukkit main thread.</p>
 */
public final class MenuSession {

    private final Plugin plugin;
    private final SessionManager manager;
    private final MenuPacketSender sender;
    private final Player player;
    private final UUID playerId;
    private final VirtualMenu menu;
    private final int containerId;
    private final AtomicInteger stateId = new AtomicInteger(1);
    private ItemStack[] lastContent;
    private boolean open;

    MenuSession(Plugin plugin, SessionManager manager, MenuPacketSender sender,
                Player player, VirtualMenu menu, int containerId) {
        this.plugin = plugin;
        this.manager = manager;
        this.sender = sender;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.menu = menu;
        this.containerId = containerId;
    }

    public Player player() {
        return player;
    }

    public VirtualMenu menu() {
        return menu;
    }

    public int containerId() {
        return containerId;
    }

    public boolean isOpen() {
        return open;
    }

    void markOpen(ItemStack[] content) {
        this.lastContent = content;
        this.open = true;
    }

    public int nextStateId() {
        return stateId.getAndIncrement();
    }

    /** Re-renders the whole menu content. Safe to call from async threads. */
    public void refresh() {
        if (Bukkit.isPrimaryThread()) {
            refreshNow();
        } else {
            Bukkit.getScheduler().runTask(plugin, this::refreshNow);
        }
    }

    private void refreshNow() {
        if (!open) {
            return;
        }
        ItemStack[] content = menu.iconSnapshot();
        lastContent = content;
        sender.sendFullContents(player, containerId, nextStateId(), content);
        sender.resetCursor(player);
    }

    /** Updates a single slot visually. Safe to call from async threads. */
    public void setSlot(int slot, MenuItem item) {
        if (Bukkit.isPrimaryThread()) {
            setSlotNow(slot, item);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> setSlotNow(slot, item));
        }
    }

    private void setSlotNow(int slot, MenuItem item) {
        if (!open || slot < 0 || slot >= menu.size().slots()) {
            return;
        }
        menu.setItem(slot, item);
        ItemStack icon = item == null ? null : item.icon();
        if (lastContent != null) {
            lastContent[slot] = icon == null ? null : icon.clone();
        }
        sender.sendSingleSlot(player, containerId, nextStateId(), slot, icon);
    }

    /** Snapshot of the last rendered icon in a slot, or null for empty. */
    public ItemStack snapshot(int slot) {
        if (lastContent == null || slot < 0 || slot >= lastContent.length) {
            return null;
        }
        ItemStack stack = lastContent[slot];
        return stack == null ? null : stack.clone();
    }

    /** Closes the menu server-side and tells the client to close the screen. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        manager.forget(playerId, this);
        try {
            menu.onClose(player);
        } finally {
            if (player.isOnline()) {
                sender.sendClose(player, containerId);
            }
        }
    }

    /** Removes the session without sending packets (player gone or dead). */
    void discard() {
        open = false;
        manager.forget(playerId, this);
    }
}
