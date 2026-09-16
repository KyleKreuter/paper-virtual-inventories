package de.kyle.virtualinventories.session;

import de.kyle.virtualinventories.menu.ClickContext;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Server-side state of one open virtual menu. Holds no real items, only the
 * last rendered visual content plus the fake window id.
 *
 * <p>All methods must be called on the Bukkit main thread.</p>
 */
public final class MenuSession {

    /** Renders the full current content (static templates + resolved dynamics). */
    @FunctionalInterface
    public interface ContentRenderer extends Supplier<ItemStack[]> {
    }

    private final Plugin plugin;
    private final SessionManager manager;
    private final MenuPacketSender sender;
    private final Player player;
    private final UUID playerId;
    private CompiledMenu menu;
    private MenuView view;
    private Map<Integer, ClickHandler> handlers;
    private MenuHooks hooks;
    private ContentRenderer renderer;
    private Component title;
    private final int containerId;
    private final AtomicInteger stateId = new AtomicInteger(1);
    private ItemStack[] lastContent;
    private boolean open;

    MenuSession(Plugin plugin, SessionManager manager, MenuPacketSender sender,
                Player player, CompiledMenu menu, MenuView view,
                Map<Integer, ClickHandler> handlers, MenuHooks hooks,
                ContentRenderer renderer, Component title, int containerId) {
        this.plugin = plugin;
        this.manager = manager;
        this.sender = sender;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.menu = menu;
        this.view = view;
        this.handlers = handlers;
        this.hooks = hooks;
        this.renderer = renderer;
        this.title = title;
        this.containerId = containerId;
    }

    public Player player() {
        return player;
    }

    public CompiledMenu menu() {
        return menu;
    }

    public MenuView view() {
        return view;
    }

    public String menuId() {
        return menu.id();
    }

    /** Title rendered when the window was opened (titles cannot change in place). */
    public Component title() {
        return title;
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

    /** Dispatches a menu-grid click to the slot's action handler. */
    public void dispatchClick(ClickContext context) {
        ClickHandler handler = context.bottom() ? null : handlers.get(context.slot());
        if (handler != null) {
            handler.handle(context);
        }
    }

    /**
     * Switches the open window to different content without closing it.
     * No flicker, no close/open sound, same window id. The new content must
     * have the same slot count and the same title (protocol limitation:
     * titles can only be set by opening a window). Hooks do not fire;
     * the player never left the screen.
     *
     * <p>Must be called on the Bukkit main thread on an open session.</p>
     */
    public void retarget(CompiledMenu menu, MenuView view,
                         Map<Integer, ClickHandler> handlers, MenuHooks hooks,
                         ContentRenderer renderer) {
        if (!open) {
            return;
        }
        this.menu = menu;
        this.view = view;
        this.handlers = handlers;
        this.hooks = hooks;
        this.renderer = renderer;
        refreshNow();
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
        ItemStack[] content = renderer.get();
        lastContent = content;
        sender.sendFullContents(player, containerId, nextStateId(), content);
        sender.syncCursor(player);
    }

    /** Updates a single slot visually. Safe to call from async threads. */
    public void setSlot(int slot, ItemStack icon) {
        if (Bukkit.isPrimaryThread()) {
            setSlotNow(slot, icon);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> setSlotNow(slot, icon));
        }
    }

    private void setSlotNow(int slot, ItemStack icon) {
        if (!open || slot < 0 || slot >= menu.size().slots()) {
            return;
        }
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
            if (hooks.onClose() != null) {
                hooks.onClose().accept(player, this);
            }
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
