package de.kyle.virtualinventories.session;

import de.kyle.virtualinventories.menu.VirtualMenu;
import de.kyle.virtualinventories.net.MenuPacketSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    public MenuSession open(Player player, VirtualMenu menu) {
        close(player);
        int containerId = ids.allocate();
        MenuSession session = new MenuSession(plugin, this, sender, player, menu, containerId);
        sessions.put(player.getUniqueId(), session);
        menu.onOpen(player, session);
        ItemStack[] content = menu.iconSnapshot();
        sender.sendOpen(player, containerId, menu.size(), menu.title());
        sender.sendFullContents(player, containerId, session.nextStateId(), content);
        sender.resetCursor(player);
        session.markOpen(content);
        return session;
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
