package de.kyle.virtualinventories.net;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantItemCost;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowProperty;
import de.kyle.virtualinventories.menu.WindowType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends container packets to fabricate a chest screen that has no
 * server-side inventory behind it.
 *
 * <p>All methods expect Bukkit item stacks and convert them internally.
 * Sending itself is thread-safe via PacketEvents, but callers should stay
 * on the main thread so item conversion never races with game logic.</p>
 */
public final class MenuPacketSender {

    public void sendOpen(Player player, int containerId, WindowType type, Component title) {
        WrapperPlayServerOpenWindow open =
                new WrapperPlayServerOpenWindow(containerId, type.typeId(), title);
        send(player, open);
    }

    public void sendFullContents(Player player, int containerId, int stateId,
                                  org.bukkit.inventory.ItemStack[] content) {
        List<ItemStack> items = new ArrayList<>(content.length);
        for (org.bukkit.inventory.ItemStack stack : content) {
            items.add(toPacket(stack));
        }
        // The client adopts the carried item as its cursor content, so this
        // must be the server-side truth and never a blind EMPTY, or a
        // legitimately held item would vanish from the player's cursor.
        WrapperPlayServerWindowItems windowItems = new WrapperPlayServerWindowItems(
                containerId, stateId, items, toPacket(player.getItemOnCursor()));
        send(player, windowItems);
    }

    public void sendSingleSlot(Player player, int containerId, int stateId,
                               int slot, org.bukkit.inventory.ItemStack icon) {
        WrapperPlayServerSetSlot setSlot =
                new WrapperPlayServerSetSlot(containerId, stateId, slot, toPacket(icon));
        send(player, setSlot);
    }

    /**
     * Syncs the client-side cursor to the server-side truth. The client predicts
     * cursor changes on click; since we cancel every click server-side, a predicted
     * pickup of a menu item (ghost item) must be reverted. But a blind EMPTY would
     * also wipe an item the player legitimately holds (e.g. grabbed before opening
     * the menu), so we always send back what the server actually has on the cursor.
     *
     * <p>Must run on the Bukkit main thread.</p>
     */
    public void syncCursor(Player player) {
        send(player, new WrapperPlayServerSetCursorItem(toPacket(player.getItemOnCursor())));
    }

    public void sendClose(Player player, int containerId) {
        send(player, new WrapperPlayServerCloseWindow(containerId));
    }

    /** Sends one container-data value (furnace progress, enchant levels, ...). */
    public void sendContainerData(Player player, int containerId, int property, int value) {
        send(player, new WrapperPlayServerWindowProperty(containerId, property, value));
    }

    /** Sends the full merchant offer list for a merchant window. */
    public void sendOffers(Player player, int containerId,
                           java.util.List<MerchantOffer> offers,
                           int villagerLevel, int villagerXp,
                           boolean showProgress, boolean canRestock) {
        send(player, new WrapperPlayServerMerchantOffers(
                containerId, offers, villagerLevel, villagerXp, showProgress, canRestock));
    }

    /** Builds one offer from a compiled trade with its current use count. */
    public static MerchantOffer toOffer(
            de.kyle.virtualinventories.serialize.CompiledForm.CompiledTrade trade, int uses) {
        MerchantItemCost first = cost(trade.buyA(), trade.buyACount());
        MerchantItemCost second = trade.buyB() == null
                ? MerchantItemCost.emptyCost()
                : cost(trade.buyB(), trade.buyBCount());
        ItemStack result = SpigotConversionUtil.fromBukkitItemStack(
                new org.bukkit.inventory.ItemStack(
                        java.util.Objects.requireNonNull(
                                org.bukkit.Material.matchMaterial(trade.result()),
                                "unknown material " + trade.result()),
                        trade.resultCount()));
        return MerchantOffer.of(first, second, result, uses, trade.maxUses(), 0, 0, 0.0F, 0);
    }

    private static MerchantItemCost cost(String materialKey, int count) {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(materialKey);
        java.util.Objects.requireNonNull(material, "unknown material " + materialKey);
        return MerchantItemCost.ofItem(SpigotConversionUtil.fromBukkitItemStack(
                new org.bukkit.inventory.ItemStack(material, count)));
    }

    private void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().getUser(player).sendPacket(packet);
    }

    private static ItemStack toPacket(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(stack);
    }
}
