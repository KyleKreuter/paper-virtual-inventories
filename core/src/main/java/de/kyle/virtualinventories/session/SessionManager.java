package de.kyle.virtualinventories.session;

import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.net.MenuPacketSender;
import de.kyle.virtualinventories.provider.CompiledMenu;
import de.kyle.virtualinventories.provider.MenuHooks;
import de.kyle.virtualinventories.provider.MenuView;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open virtual menus per player. At most one session exists per player,
 * opening a new menu closes the previous one first.
 *
 * <p>Open/close must run on the Bukkit main thread.</p>
 */
public final class SessionManager {

    private final Plugin plugin;
    private final MenuPacketSender sender;
    private final WindowIdAllocator ids = new WindowIdAllocator();
    private final ConcurrentHashMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public SessionManager(Plugin plugin, MenuPacketSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    public MenuSession openSession(Player player, CompiledMenu menu, MenuView view,
                                   Map<Integer, ClickHandler> handlers, MenuHooks hooks,
                                   Component title, ItemStack[] content,
                                   MenuSession.ContentRenderer renderer) {
        close(player);
        int containerId = ids.allocate();
        MenuSession session = new MenuSession(plugin, this, sender, player, menu, view,
                handlers, hooks == null ? MenuHooks.empty() : hooks, renderer, title, containerId);
        sessions.put(player.getUniqueId(), session);
        sender.sendOpen(player, containerId, menu.windowType(), title);
        sender.sendFullContents(player, containerId, session.nextStateId(), content);
        sender.syncCursor(player);
        session.markOpen(content);
        if (session.menu() != null && hooks != null && hooks.onOpen() != null) {
            hooks.onOpen().accept(player, session);
        }
        return session;
    }

    /**
     * Switches the player's open window to different content without closing it.
     * Used for navigation (e.g. menu pages): no flicker, no sound, same window id,
     * and no window open/close roundtrip that could disturb the client cursor.
     *
     * <p>Falls back to {@link #openSession} (close + reopen) when no menu is open
     * or the new content has a different size or title — titles can only be set
     * by opening a window (protocol limitation).</p>
     *
     * <p>Must run on the Bukkit main thread.</p>
     */
    public MenuSession switchSession(Player player, CompiledMenu menu, MenuView view,
                                     Map<Integer, ClickHandler> handlers, MenuHooks hooks,
                                     Component title, ItemStack[] content,
                                     MenuSession.ContentRenderer renderer) {
        MenuSession current = sessions.get(player.getUniqueId());
        if (current != null && current.isOpen()
                && current.menu().windowType() == menu.windowType()
                && current.menu().slotCount() == menu.slotCount()
                && current.title().equals(title)) {
            current.retarget(menu, view, handlers,
                    hooks == null ? MenuHooks.empty() : hooks, renderer);
            return current;
        }
        if (current != null) {
            plugin.getLogger().info("switchSession falls back to reopen for '" + menu.id()
                    + "' (current=" + current.menu().id() + "/" + current.menu().windowType()
                    + (current.isOpen() ? "" : "/closed") + ", new=" + menu.windowType() + ")");
        }
        return openSession(player, menu, view, handlers, hooks, title, content, renderer);
    }

    /** Applies rename text typed into the player's open anvil menu, if any. */
    public void handleRename(Player player, String text) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null && session.isOpen()) {
            session.handleRename(text);
        }
    }

    public void close(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.close();
        }
    }

    /** Refreshes the player's screen without firing close events. */
    public void resync(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null && session.isOpen()) {
            session.refresh();
        }
    }

    public MenuSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public MenuSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean isOpen(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        return session != null && session.isOpen();
    }

    public boolean ownsWindow(UUID playerId, int containerId) {
        MenuSession session = sessions.get(playerId);
        return session != null && session.isOpen() && session.containerId() == containerId;
    }

    void forget(UUID playerId, MenuSession session) {
        sessions.remove(playerId, session);
    }

    /** Drops the session silently, used when the player quit or died. */
    public void discard(Player player) {
        MenuSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.discard();
        }
    }

    public void closeAll() {
        for (MenuSession session : sessions.values()) {
            try {
                session.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close menu session: " + e.getMessage());
            }
        }
        sessions.clear();
    }

    public boolean isVirtualViewer(UUID playerId) {
        MenuSession session = sessions.get(playerId);
        return session != null && session.isOpen();
    }

    /** Closes sessions of players that are no longer online. Safety net for reloads. */
    public void purgeOffline() {
        sessions.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
