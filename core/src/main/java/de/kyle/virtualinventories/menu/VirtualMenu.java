package de.kyle.virtualinventories.menu;

import de.kyle.virtualinventories.session.MenuSession;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Base class for a dupe-proof virtual menu.
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>All items are visual only. They are sent to the client via window packets
 *       and never exist in any server-side inventory.</li>
 *   <li>Clicks arrive as cancelled window-click packets and are dispatched to
 *       {@link #onMenuClick} / per-slot handlers on the main thread.</li>
 *   <li>Override {@link #onOpen} to fill initial content, {@link #onClose} for cleanup.</li>
 * </ul>
 */
public abstract class VirtualMenu {

    protected final Component title;
    protected final MenuSize size;
    protected final MenuItem[] items;

    protected VirtualMenu(Component title, MenuSize size) {
        this.title = title;
        this.size = size;
        this.items = new MenuItem[size.slots()];
    }

    public Component title() {
        return title;
    }

    public MenuSize size() {
        return size;
    }

    public void setItem(int slot, MenuItem item) {
        checkSlot(slot);
        items[slot] = item;
    }

    public void setItem(int slot, ItemStack icon, ClickHandler handler) {
        setItem(slot, MenuItem.of(icon, handler));
    }

    public void clear(int slot) {
        checkSlot(slot);
        items[slot] = null;
    }

    public void fillBorder(MenuItem item) {
        int slots = size.slots();
        int rows = size.rows();
        for (int col = 0; col < 9; col++) {
            items[col] = item;
            items[slots - 9 + col] = item;
        }
        for (int row = 1; row < rows - 1; row++) {
            items[row * 9] = item;
            items[row * 9 + 8] = item;
        }
    }

    public MenuItem getItem(int slot) {
        checkSlot(slot);
        return items[slot];
    }

    /** Snapshot of current icons for packet rendering. Null entries mean empty slots. */
    public ItemStack[] iconSnapshot() {
        ItemStack[] snapshot = new ItemStack[size.slots()];
        for (int i = 0; i < items.length; i++) {
            MenuItem item = items[i];
            snapshot[i] = item == null || item.icon() == null ? null : item.icon().clone();
        }
        return snapshot;
    }

    /** Called on the main thread right before the open-window packet is sent. */
    public void onOpen(Player player, MenuSession session) {
    }

    /** Called on the main thread after the session was closed (by player or server). */
    public void onClose(Player player) {
    }

    /**
     * Called on the main thread for clicks inside the menu grid.
     * Default behavior dispatches to the per-slot handler if present.
     */
    protected void onMenuClick(ClickContext context) {
        MenuItem item = context.slot() >= 0 ? items[context.slot()] : null;
        if (item != null && item.hasHandler()) {
            item.handler().handle(context);
        }
    }

    /**
     * Called on the main thread for clicks on the bottom (player inventory) area
     * while the menu is open. Default: ignore (packets are cancelled anyway,
     * so no item can move).
     */
    protected void onBottomClick(ClickContext context) {
    }

    /** Dispatches a click to the bottom or menu handler. Called by the framework. */
    public final void dispatchClick(ClickContext context) {
        if (context.bottom()) {
            onBottomClick(context);
        } else {
            onMenuClick(context);
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size.slots()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of menu bounds " + size.slots());
        }
    }
}
