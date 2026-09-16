package de.kyle.virtualinventories.net;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import de.kyle.virtualinventories.menu.ClickContext;
import de.kyle.virtualinventories.menu.ClickType;
import de.kyle.virtualinventories.session.MenuSession;
import de.kyle.virtualinventories.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Intercepts all container interaction packets while a virtual menu is open.
 *
 * <p>This is the core of the dupe protection: the server never opened a real
 * container, so every click/close/creative packet from the client is cancelled
 * before vanilla can process it. Clicks are only interpreted as UI events.</p>
 *
 * <p>Runs on the Netty thread. All game logic is deferred to the main thread.</p>
 */
public final class PacketMenuListener implements PacketListener {

    private final Plugin plugin;
    private final SessionManager sessions;

    public PacketMenuListener(Plugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID playerId = event.getUser().getUUID();
        if (playerId == null || !sessions.isVirtualViewer(playerId)) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClick(event, playerId);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            // Client closed the screen (ESC, E, ...). Swallow it, there is no
            // server-side container to close, and clean up the session.
            event.setCancelled(true);
            scheduleClose(playerId);
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW_BUTTON
                || event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            // Beacon/enchant buttons and creative item spawns have no meaning
            // in a fake window. Cancel to avoid client/server desync.
            event.setCancelled(true);
        }
    }

    private void handleClick(PacketReceiveEvent event, UUID playerId) {
        final int windowId;
        final int rawSlot;
        final int button;
        final String clickName;
        try {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            windowId = click.getWindowId();
            rawSlot = click.getSlot();
            button = click.getButton();
            clickName = click.getWindowClickType().name();
        } catch (Exception e) {
            // Never let a malformed packet break the pipeline. Cancel and resync.
            event.setCancelled(true);
            plugin.getLogger().warning("Dropped malformed click packet from " + playerId);
            resync(playerId);
            return;
        }

        if (!sessions.ownsWindow(playerId, windowId)) {
            // Click for any other window (e.g. forged id 0) while a menu is open.
            // Cancel: the player must not touch their real inventory behind the menu.
            event.setCancelled(true);
            resync(playerId);
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> dispatchClick(playerId, windowId, rawSlot, button, clickName));
    }

    private void dispatchClick(UUID playerId, int windowId, int rawSlot, int button, String clickName) {
        Player player = Bukkit.getPlayer(playerId);
        MenuSession session = sessions.get(playerId);
        if (player == null || session == null || !session.isOpen() || session.containerId() != windowId) {
            return;
        }

        int menuSlots = session.menu().size().slots();
        boolean bottom = rawSlot < 0 || rawSlot >= menuSlots;
        int slot = bottom ? -1 : rawSlot;
        ItemStack snapshot = bottom ? null : session.snapshot(slot);
        ClickType type = ClickType.fromPacket(clickName, button);
        ClickContext context = new ClickContext(player, session, slot, rawSlot, type, button, snapshot, bottom);

        try {
            session.menu().dispatchClick(context);
        } catch (Exception e) {
            plugin.getLogger().warning("Menu click handler failed: " + e.getMessage());
        }

        // Wipe the client-predicted cursor/item movement. If the handler already
        // refreshed or closed the menu, this is a harmless no-op / skipped.
        if (session.isOpen()) {
            session.refresh();
        }
    }

    private void scheduleClose(UUID playerId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            MenuSession session = sessions.get(playerId);
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to close menu session: " + e.getMessage());
                }
            }
        });
    }

    private void resync(UUID playerId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                sessions.resync(player);
            }
        });
    }
}
