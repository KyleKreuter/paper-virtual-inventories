package de.kyle.virtualinventories.session;

import de.kyle.virtualinventories.menu.ClickContext;
import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.menu.WindowType;
import de.kyle.virtualinventories.net.MenuPacketSender;
import de.kyle.virtualinventories.provider.CompiledMenu;
import de.kyle.virtualinventories.provider.MenuHooks;
import de.kyle.virtualinventories.provider.MenuView;
import de.kyle.virtualinventories.provider.TradeEngine;
import de.kyle.virtualinventories.serialize.CompiledForm;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private long lastRenameNanos;
    private final Map<CompiledForm.CompiledTrade, Integer> tradeUses = new HashMap<>();
    private Map<String, ClickHandler> actionHandlers = Map.of();
    /** Stateful output visuals (merchant results, furnace products set via setSlot). */
    private final Map<Integer, ItemStack> outputVisuals = new HashMap<>();

    MenuSession(Plugin plugin, SessionManager manager, MenuPacketSender sender,
                Player player, CompiledMenu menu, MenuView view,
                Map<Integer, ClickHandler> handlers, Map<String, ClickHandler> actionHandlers,
                MenuHooks hooks, ContentRenderer renderer, Component title, int containerId) {
        this.plugin = plugin;
        this.manager = manager;
        this.sender = sender;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.menu = menu;
        this.view = view;
        this.handlers = handlers;
        this.actionHandlers = Map.copyOf(actionHandlers);
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

    /** Dispatches a window button click (enchantment table, ...) to its action handler. */
    public void fireButton(int buttonId) {
        if (!open) {
            return;
        }
        String actionId = menu.buttonAction(buttonId);
        if (actionId == null) {
            return;
        }
        ClickHandler handler = actionHandlers.get(actionId);
        if (handler == null) {
            return;
        }
        handler.handle(new ClickContext(player, this, -1, -1,
                de.kyle.virtualinventories.menu.ClickType.UNKNOWN, buttonId, null, false));
    }

    /** Action handlers by action id, used for window buttons. Set by the provider. */
    public void setActionHandlers(Map<String, ClickHandler> actionHandlers) {
        this.actionHandlers = Map.copyOf(actionHandlers);
    }

    /** Sends container data and merchant offers. Runs after opening/retargeting. */
    void sendExtras() {
        for (Map.Entry<Integer, Integer> data : menu.containerData().entrySet()) {
            sender.sendContainerData(player, containerId, data.getKey(), data.getValue());
        }
        resendOffers();
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
                         Map<Integer, ClickHandler> handlers, Map<String, ClickHandler> actionHandlers,
                         MenuHooks hooks, ContentRenderer renderer) {
        if (!open) {
            return;
        }
        this.menu = menu;
        this.view = view;
        this.handlers = handlers;
        this.actionHandlers = Map.copyOf(actionHandlers);
        this.hooks = hooks;
        this.renderer = renderer;
        this.outputVisuals.clear();
        refreshNow();
        sendExtras();
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
        // Deposit slots hold real player items server-side; the renderer only
        // produces visuals, so re-apply deposits after every re-render.
        if (lastContent != null && !menu.depositSlots().isEmpty()) {
            for (int slot : menu.depositSlots()) {
                if (slot >= 0 && slot < lastContent.length && slot < content.length) {
                    ItemStack kept = lastContent[slot];
                    if (kept != null && !kept.getType().isAir()) {
                        content[slot] = kept;
                    }
                }
            }
        }
        // Output slots hold stateful visuals (merchant results, furnace products
        // set via setSlot); re-apply them so re-renders don't wipe them.
        if (lastContent != null && !outputVisuals.isEmpty()) {
            for (Map.Entry<Integer, ItemStack> visual : outputVisuals.entrySet()) {
                int slot = visual.getKey();
                if (slot >= 0 && slot < content.length) {
                    content[slot] = visual.getValue().clone();
                }
            }
        }
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
        if (!open || slot < 0 || slot >= menu.slotCount()) {
            return;
        }
        if (lastContent != null) {
            lastContent[slot] = icon == null ? null : icon.clone();
        }
        if (menu.isOutputSlot(slot)) {
            if (icon == null || icon.getType().isAir()) {
                outputVisuals.remove(slot);
            } else {
                outputVisuals.put(slot, icon.clone());
            }
        }
        sender.sendSingleSlot(player, containerId, nextStateId(), slot, icon);
    }

    /**
     * Returns a copy of the server-side stack in a deposit slot
     * (or an empty stack when none). Used by actions that consume inputs
     * (stonecutting, weaving, enchanting, ...).
     */
    public ItemStack deposit(int slot) {
        return depositStack(slot).clone();
    }

    /**
     * Sets the server-side stack of a deposit slot (cloned). Only meaningful
     * for deposit slots; other slots are re-rendered on refresh.
     */
    public void setDeposit(int slot, ItemStack stack) {
        if (!menu.isDepositSlot(slot)) {
            return;
        }
        setDepositStack(slot, stack);
    }

    /** Snapshot of the last rendered icon in a slot, or null for empty. */
    public ItemStack snapshot(int slot) {
        if (lastContent == null || slot < 0 || slot >= lastContent.length) {
            return null;
        }
        ItemStack stack = lastContent[slot];
        return stack == null ? null : stack.clone();
    }

    /**
     * Applies a rename text typed into an anvil window. Throttled (100 ms):
     * the client sends a packet per keystroke. Texts longer than 50 chars
     * are truncated (vanilla anvil limit).
     */
    public void handleRename(String text) {
        if (!open || !menu.windowType().isAnvil()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastRenameNanos < 100_000_000L) {
            return;
        }
        lastRenameNanos = now;
        String clean = text == null ? "" : text;
        view.text(clean.length() > 50 ? clean.substring(0, 50) : clean);
    }

    /**
     * Moves items between a deposit slot and the player, fully server-side.
     * The client prediction was already cancelled; only these mutations stick.
     */
    public void handleDeposit(int rawSlot, de.kyle.virtualinventories.menu.ClickType type, int button) {
        if (!open || !menu.windowType().allowsDeposit()) {
            return;
        }
        boolean bottom = rawSlot < 0 || rawSlot >= menu.slotCount();
        if (!bottom && !menu.isDepositSlot(rawSlot)) {
            return;
        }
        switch (type) {
            case LEFT -> {
                if (!bottom) {
                    swapWithCursor(rawSlot);
                }
            }
            case RIGHT -> {
                if (!bottom) {
                    placeOrTakeOne(rawSlot);
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> transfer(rawSlot, bottom);
            case DROP -> {
                if (!bottom) {
                    dropStack(rawSlot);
                }
            }
            default -> {
                // Anything else (number keys, drags, ...) is cancelled with no effect.
            }
        }
        refreshMerchantOutput();
        sender.syncCursor(player);
    }

    /**
     * Sends one container-data value live (furnace progress, enchant levels).
     * Safe to call from async threads.
     */
    public void setContainerData(int property, int value) {
        if (!open) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            sender.sendContainerData(player, containerId, property, value);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (open) {
                    sender.sendContainerData(player, containerId, property, value);
                }
            });
        }
    }

    /**
     * Takes the visual stack from a non-merchant output slot (furnace result,
     * ...). Returns the taken stack, or null when empty. The caller hands it
     * to the player (cursor first, then inventory).
     */
    public ItemStack takeOutputSlot(int slot) {
        if (!open || !menu.isOutputSlot(slot) || !menu.trades().isEmpty()) {
            return null;
        }
        ItemStack inSlot = depositStack(slot);
        if (inSlot.getType().isAir()) {
            return null;
        }
        setDepositStack(slot, ItemStack.empty());
        outputVisuals.remove(slot);
        return inSlot.clone();
    }

    /**
     * Executes a merchant trade: consumes inputs, hands over a real result
     * item, bumps uses and resends the offer list. Returns true on success.
     */
    public boolean tryMerchantTrade() {
        if (!open || menu.trades().isEmpty()) {
            return false;
        }
        List<Integer> inputs = menu.depositSlots().stream()
                .filter(s -> !menu.isOutputSlot(s)).sorted().toList();
        if (inputs.isEmpty()) {
            return false;
        }
        int first = inputs.get(0);
        int second = inputs.size() > 1 ? inputs.get(1) : -1;
        TradeEngine.SlotContent a = slotContent(first);
        TradeEngine.SlotContent b =
                second < 0 ? TradeEngine.SlotContent.empty() : slotContent(second);
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(
                menu.trades(), a, b, t -> tradeUses.getOrDefault(t, 0));
        if (match.isEmpty()) {
            return false;
        }
        TradeEngine.Match hit = match.get();
        consume(first, hit.consumeA());
        if (second >= 0 && hit.consumeB() > 0) {
            consume(second, hit.consumeB());
        }
        CompiledForm.CompiledTrade trade = hit.trade();
        ItemStack result = new ItemStack(Objects.requireNonNull(
                Material.matchMaterial(trade.result()), "unknown material " + trade.result()),
                trade.resultCount());
        if (player.getItemOnCursor().getType().isAir()) {
            player.setItemOnCursor(result);
        } else {
            player.getInventory().addItem(result);
            player.updateInventory();
        }
        tradeUses.merge(trade, 1, Integer::sum);
        resendOffers();
        refreshMerchantOutput();
        return true;
    }

    /** Re-sends the merchant offer list with current use counts. */
    public void resendOffers() {
        if (!open || menu.trades().isEmpty()) {
            return;
        }
        List<com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer> offers =
                new ArrayList<>(menu.trades().size());
        for (CompiledForm.CompiledTrade trade : menu.trades()) {
            offers.add(MenuPacketSender.toOffer(trade, tradeUses.getOrDefault(trade, 0)));
        }
        sender.sendOffers(player, containerId, offers, 0, 0, false, true);
    }

    private TradeEngine.SlotContent slotContent(int slot) {
        ItemStack stack = depositStack(slot);
        if (stack.getType().isAir()) {
            return TradeEngine.SlotContent.empty();
        }
        return new TradeEngine.SlotContent(stack.getType().name(), stack.getAmount());
    }

    private void consume(int slot, int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack stack = depositStack(slot);
        if (stack.getAmount() <= amount) {
            setDepositStack(slot, ItemStack.empty());
        } else {
            stack.setAmount(stack.getAmount() - amount);
            setDepositStack(slot, stack);
        }
    }

    /**
     * Updates the merchant result visual from the current inputs. Vanilla
     * merchants show the result only when a trade matches; we do the same.
     */
    private void refreshMerchantOutput() {
        if (!open || menu.trades().isEmpty() || menu.outputSlots().isEmpty()) {
            return;
        }
        List<Integer> inputs = menu.depositSlots().stream()
                .filter(s -> !menu.isOutputSlot(s)).sorted().toList();
        TradeEngine.SlotContent a = inputs.isEmpty()
                ? TradeEngine.SlotContent.empty() : slotContent(inputs.get(0));
        TradeEngine.SlotContent b = inputs.size() < 2
                ? TradeEngine.SlotContent.empty() : slotContent(inputs.get(1));
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(
                menu.trades(), a, b, t -> tradeUses.getOrDefault(t, 0));
        int outSlot = menu.outputSlots().stream().sorted().findFirst().orElse(-1);
        if (outSlot < 0) {
            return;
        }
        ItemStack visual = ItemStack.empty();
        if (match.isPresent()) {
            CompiledForm.CompiledTrade trade = match.get().trade();
            visual = new ItemStack(Objects.requireNonNull(
                    Material.matchMaterial(trade.result()), "unknown material " + trade.result()),
                    trade.resultCount());
        }
        setSlotNow(outSlot, visual);
    }

    private ItemStack depositStack(int slot) {
        if (lastContent == null || slot < 0 || slot >= lastContent.length) {
            return ItemStack.empty();
        }
        ItemStack stack = lastContent[slot];
        return stack == null ? ItemStack.empty() : stack;
    }

    private void setDepositStack(int slot, ItemStack stack) {
        ItemStack copy = stack == null || stack.getType().isAir() ? ItemStack.empty() : stack.clone();
        if (lastContent != null && slot >= 0 && slot < lastContent.length) {
            lastContent[slot] = copy;
        }
        sender.sendSingleSlot(player, containerId, nextStateId(), slot, copy);
    }

    private void swapWithCursor(int slot) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack inSlot = depositStack(slot);
        player.setItemOnCursor(inSlot.getType().isAir() ? ItemStack.empty() : inSlot.clone());
        setDepositStack(slot, cursor);
    }

    private void placeOrTakeOne(int slot) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack inSlot = depositStack(slot);
        boolean cursorEmpty = cursor.getType().isAir();
        boolean slotEmpty = inSlot.getType().isAir();
        if (cursorEmpty && !slotEmpty) {
            // Pick up half (rounded up).
            int take = (inSlot.getAmount() + 1) / 2;
            ItemStack taken = inSlot.clone();
            taken.setAmount(take);
            player.setItemOnCursor(taken);
            if (inSlot.getAmount() <= take) {
                setDepositStack(slot, ItemStack.empty());
            } else {
                inSlot.setAmount(inSlot.getAmount() - take);
                setDepositStack(slot, inSlot);
            }
        } else if (!cursorEmpty && (slotEmpty || (inSlot.isSimilar(cursor)
                && inSlot.getAmount() < inSlot.getMaxStackSize()))) {
            // Place a single item.
            ItemStack one = cursor.clone();
            one.setAmount(1);
            if (slotEmpty) {
                setDepositStack(slot, one);
            } else {
                inSlot.setAmount(inSlot.getAmount() + 1);
                setDepositStack(slot, inSlot);
            }
            if (cursor.getAmount() <= 1) {
                player.setItemOnCursor(ItemStack.empty());
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor);
            }
        }
    }

    private void transfer(int rawSlot, boolean bottom) {
        if (!bottom) {
            // Deposit slot -> player inventory.
            ItemStack inSlot = depositStack(rawSlot);
            if (inSlot.getType().isAir()) {
                return;
            }
            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(inSlot.clone());
            ItemStack rest = leftovers.values().stream().findFirst().orElse(ItemStack.empty());
            setDepositStack(rawSlot, rest);
        } else {
            // Player inventory -> first empty deposit slot.
            int playerSlot = toPlayerSlot(rawSlot);
            if (playerSlot < 0) {
                return;
            }
            ItemStack carried = player.getInventory().getItem(playerSlot);
            if (carried == null || carried.getType().isAir()) {
                return;
            }
            for (int deposit : menu.depositSlots()) {
                if (depositStack(deposit).getType().isAir()) {
                    setDepositStack(deposit, carried.clone());
                    player.getInventory().setItem(playerSlot, ItemStack.empty());
                    player.updateInventory();
                    return;
                }
            }
        }
    }

    private void dropStack(int slot) {
        ItemStack inSlot = depositStack(slot);
        if (inSlot.getType().isAir()) {
            return;
        }
        setDepositStack(slot, ItemStack.empty());
        player.getWorld().dropItemNaturally(player.getLocation(), inSlot.clone());
    }

    /** Maps a bottom raw slot to a player inventory slot index, or -1. */
    private int toPlayerSlot(int rawSlot) {
        int idx = rawSlot - menu.slotCount();
        if (idx < 0 || idx >= 36) {
            return -1;
        }
        return idx < 27 ? idx + 9 : idx - 27;
    }

    /**
     * Returns all deposited items to the player. Runs on close and on silent
     * discard (quit/death): leftovers go to the inventory, or drop at the
     * player's feet when online and full.
     */
    private void returnDeposits() {
        if (menu.depositSlots().isEmpty() || lastContent == null) {
            return;
        }
        for (int slot : menu.depositSlots()) {
            if (slot < 0 || slot >= lastContent.length) {
                continue;
            }
            ItemStack stack = lastContent[slot];
            lastContent[slot] = ItemStack.empty();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            for (ItemStack rest : leftovers.values()) {
                if (player.isOnline()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                } else {
                    plugin.getLogger().warning("Menu '" + menu.id()
                            + "' dropped deposited items for offline player " + player.getName()
                            + " (inventory full)");
                }
            }
        }
        if (player.isOnline()) {
            player.updateInventory();
        }
    }

    /** Closes the menu server-side and tells the client to close the screen. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        returnDeposits();
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
        returnDeposits();
        manager.forget(playerId, this);
    }
}
