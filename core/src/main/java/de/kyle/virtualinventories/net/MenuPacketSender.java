package de.kyle.virtualinventories.net;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import de.kyle.virtualinventories.menu.MenuSize;
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

    public void sendOpen(Player player, int containerId, MenuSize size, Component title) {
        WrapperPlayServerOpenWindow open =
                new WrapperPlayServerOpenWindow(containerId, size.windowTypeId(), title);
        send(player, open);
    }

    public void sendFullContents(Player player, int containerId, int stateId,
                                 org.bukkit.inventory.ItemStack[] content) {
        List<ItemStack> items = new ArrayList<>(content.length);
        for (org.bukkit.inventory.ItemStack stack : content) {
            items.add(toPacket(stack));
        }
        WrapperPlayServerWindowItems windowItems =
                new WrapperPlayServerWindowItems(containerId, stateId, items, ItemStack.EMPTY);
        send(player, windowItems);
    }

    public void sendSingleSlot(Player player, int containerId, int stateId,
                               int slot, org.bukkit.inventory.ItemStack icon) {
        WrapperPlayServerSetSlot setSlot =
                new WrapperPlayServerSetSlot(containerId, stateId, slot, toPacket(icon));
        send(player, setSlot);
    }

    /**
     * Clears the client-side cursor. The client predicts cursor changes on click,
     * since we cancel every click server-side we must wipe the predicted cursor
     * or ghost items appear.
     */
    public void resetCursor(Player player) {
        send(player, new WrapperPlayServerSetCursorItem(ItemStack.EMPTY));
    }

    public void sendClose(Player player, int containerId) {
        send(player, new WrapperPlayServerCloseWindow(containerId));
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
