package de.kyle.virtualinventories.net;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
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
        } else if (event.getPacketType() == PacketType.Play.Client.NAME_ITEM) {
            // Anvil rename keystroke. The server never opened a real anvil, so
            // vanilla must not see this — store the text server-side instead.
            event.setCancelled(true);
            handleRename(event, playerId);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            // Client closed the screen (ESC, E, ...). Swallow it, there is no
            // server-side container to close, and clean up the session.
            event.setCancelled(true);
            scheduleClose(playerId);
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW_BUTTON) {
            // Window buttons (enchantment table, ...). Vanilla must not see
            // them; mapped buttons fire menu actions instead.
            event.setCancelled(true);
            handleButton(event, playerId);
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            // Creative item spawns have no meaning in a fake window. Cancel to
            // avoid client/server desync.
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

    private void handleRename(PacketReceiveEvent event, UUID playerId) {
        final String text;
        try {
            text = new WrapperPlayClientNameItem(event).getItemName();
        } catch (Exception e) {
            plugin.getLogger().warning("Dropped malformed rename packet from " + playerId);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                try {
                    sessions.handleRename(player, text);
                } catch (Exception e) {
                    plugin.getLogger().warning("Rename handling failed: " + e.getMessage());
                }
            }
        });
    }

    private void dispatchClick(UUID playerId, int windowId, int rawSlot, int button, String clickName) {
        Player player = Bukkit.getPlayer(playerId);
        MenuSession session = sessions.get(playerId);
        if (player == null || session == null || !session.isOpen() || session.containerId() != windowId) {
            return;
        }

        int menuSlots = session.menu().slotCount();
        boolean bottom = rawSlot < 0 || rawSlot >= menuSlots;
        int slot = bottom ? -1 : rawSlot;
        ClickType type = ClickType.fromPacket(clickName, button);

        // Output slots hand over real items: merchant results execute a trade,
        // other outputs (furnace result, ...) give the displayed stack.
        if (!bottom && session.menu().isOutputSlot(slot)) {
            try {
                if (!session.menu().trades().isEmpty()) {
                    session.tryMerchantTrade();
                } else {
                    ItemStack taken = session.takeOutputSlot(slot);
                    if (taken != null) {
                        giveToPlayer(player, taken);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Output handling failed: " + e.getMessage());
            }
            if (session.isOpen()) {
                session.refresh();
            }
            return;
        }

        // Deposit slots move real player items server-side; everything else is
        // a UI event. Shift-clicks from the player inventory fill deposits.
        if (session.menu().windowType().allowsDeposit()
                && ((slot >= 0 && session.menu().isDepositSlot(slot))
                    || (bottom && (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT)))) {
            try {
                session.handleDeposit(rawSlot, type, button);
            } catch (Exception e) {
                plugin.getLogger().warning("Deposit handling failed: " + e.getMessage());
            }
            if (session.isOpen()) {
                session.refresh();
            }
            return;
        }

        ItemStack snapshot = bottom ? null : session.snapshot(slot);
        ClickContext context = new ClickContext(player, session, slot, rawSlot, type, button, snapshot, bottom);

        try {
            session.dispatchClick(context);
        } catch (Exception e) {
            plugin.getLogger().warning("Menu click handler failed: " + e.getMessage());
        }

        // Wipe the client-predicted cursor/item movement. If the handler already
        // refreshed or closed the menu, this is a harmless no-op / skipped.
        if (session.isOpen()) {
            session.refresh();
        }
    }

    private void handleButton(PacketReceiveEvent event, UUID playerId) {
        final int windowId;
        final int buttonId;
        try {
            com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindowButton
                    button = new com.github.retrooper.packetevents.wrapper.play.client
                            .WrapperPlayClientClickWindowButton(event);
            windowId = button.getWindowId();
            buttonId = button.getButtonId();
        } catch (Exception e) {
            plugin.getLogger().warning("Dropped malformed button packet from " + playerId);
            return;
        }
        if (!sessions.ownsWindow(playerId, windowId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            MenuSession session = sessions.get(playerId);
            if (player == null || session == null || !session.isOpen()
                    || session.containerId() != windowId) {
                return;
            }
            try {
                session.fireButton(buttonId);
            } catch (Exception e) {
                plugin.getLogger().warning("Menu button handler failed: " + e.getMessage());
            }
            if (session.isOpen()) {
                session.refresh();
            }
        });
    }

    private static void giveToPlayer(Player player, ItemStack taken) {
        if (player.getItemOnCursor().getType().isAir()) {
            player.setItemOnCursor(taken);
        } else {
            player.getInventory().addItem(taken);
            player.updateInventory();
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
