package de.kyle.virtualinventories;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import de.kyle.virtualinventories.net.MenuBukkitListener;
import de.kyle.virtualinventories.net.MenuPacketSender;
import de.kyle.virtualinventories.net.PacketMenuListener;
import de.kyle.virtualinventories.provider.InMemoryMenuProvider;
import de.kyle.virtualinventories.provider.MenuProvider;
import de.kyle.virtualinventories.session.MenuSession;
import de.kyle.virtualinventories.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Entry point of the library.
 *
 * <p>Usage in your plugin:</p>
 * <pre>{@code
 * public void onEnable() {
 *     VirtualInventories.init(this);
 *     MenuProvider menus = VirtualInventories.api().menus();
 *     menus.loadDirectory(); // menus/*.yml -> compiled/*.vmenu.gz
 *     menus.action("close", ClickHandler.close());
 *     getCommand("shop").setExecutor((s, c, l, a) -> {
 *         VirtualInventories.api().open((Player) s, "shop");
 *         return true;
 *     });
 * }
 *
 * public void onDisable() {
 *     VirtualInventories.api().shutdown();
 * }
 * }</pre>
 *
 * <p>Requires the PacketEvents plugin on the server
 * ({@code depend: [packetevents]} in your plugin.yml).</p>
 */
public final class VirtualInventories {

    private static VirtualInventories instance;

    private final Plugin plugin;
    private final MenuPacketSender sender;
    private final SessionManager sessions;
    private final MenuProvider menus;
    private final PacketMenuListener packetListener;
    private final MenuBukkitListener bukkitListener;
    private PacketListenerCommon registration;
    private boolean shutDown;

    private VirtualInventories(Plugin plugin) {
        this.plugin = plugin;
        this.sender = new MenuPacketSender();
        this.sessions = new SessionManager(plugin, sender);
        this.menus = new InMemoryMenuProvider(plugin, sessions);
        this.packetListener = new PacketMenuListener(plugin, sessions);
        this.bukkitListener = new MenuBukkitListener(sessions);
    }

    /**
     * Initializes the library. Safe to call once per plugin enable.
     * Registers the packet interceptor and session cleanup listeners.
     */
    public static synchronized VirtualInventories init(Plugin plugin) {
        if (instance != null && !instance.shutDown) {
            return instance;
        }
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PacketEvents classes not found. "
                    + "Add packetevents-spigot as dependency and 'depend: [packetevents]' to plugin.yml.", e);
        }
        VirtualInventories api = new VirtualInventories(plugin);
        api.registration = PacketEvents.getAPI().getEventManager()
                .registerListener(api.packetListener, PacketListenerPriority.HIGH);
        Bukkit.getPluginManager().registerEvents(api.bukkitListener, plugin);
        instance = api;
        return api;
    }

    public static synchronized VirtualInventories api() {
        if (instance == null || instance.shutDown) {
            throw new IllegalStateException("VirtualInventories not initialized. Call init(plugin) in onEnable().");
        }
        return instance;
    }

    /** The menu provider: register menus, placeholders and click actions here. */
    public MenuProvider menus() {
        return menus;
    }

    /** Opens a compiled menu for the player. Must be called on the main thread. */
    public MenuSession open(Player player, String menuId) {
        ensureActive();
        return menus.open(player, menuId);
    }

    /** Opens a compiled menu with per-open extra placeholder values. */
    public MenuSession open(Player player, String menuId, Map<String, String> extra) {
        ensureActive();
        return menus.open(player, menuId, extra);
    }

    /**
     * Switches the player's open window to another menu in place (no flicker).
     * Falls back to close + reopen when no menu is open or size/title differ.
     */
    public MenuSession switchTo(Player player, String menuId) {
        ensureActive();
        return menus.switchTo(player, menuId);
    }

    /** Variant of {@link #switchTo(Player, String)} with per-open extra values. */
    public MenuSession switchTo(Player player, String menuId, Map<String, String> extra) {
        ensureActive();
        return menus.switchTo(player, menuId, extra);
    }

    /** Closes the player's virtual menu if one is open. Safe no-op otherwise. */
    public void close(Player player) {
        sessions.close(player);
    }

    public boolean isOpen(Player player) {
        return sessions.isOpen(player);
    }

    public SessionManager sessions() {
        return sessions;
    }

    /** Closes all menus and unregisters listeners. Call in onDisable(). */
    public synchronized void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        try {
            if (registration != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(registration);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister packet listener: " + e.getMessage());
        }
        menus.shutdown();
        if (instance == this) {
            instance = null;
        }
    }

    private void ensureActive() {
        if (shutDown) {
            throw new IllegalStateException("VirtualInventories is shut down.");
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Virtual menu open/close must run on the main thread.");
        }
    }
}
