package de.kyle.virtualinventories.demo;

import de.kyle.virtualinventories.VirtualInventories;
import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.menu.MenuItem;
import de.kyle.virtualinventories.menu.MenuSize;
import de.kyle.virtualinventories.menu.PaginatedMenu;
import de.kyle.virtualinventories.menu.VirtualMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo plugin: proves that a full chest GUI works without any real inventory.
 * Open with /vmenu (simple) or /vpaged (pagination + async refresh example).
 */
public final class DemoPlugin extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        VirtualInventories.init(this);
        getCommand("vmenu").setExecutor(this);
        getCommand("vpaged").setExecutor(this);
    }

    @Override
    public void onDisable() {
        try {
            VirtualInventories.api().shutdown();
        } catch (IllegalStateException ignored) {
            // Never initialized, nothing to do.
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("vpaged")) {
            VirtualInventories.api().open(player, new DemoPagedMenu());
        } else {
            VirtualInventories.api().open(player, new DemoMenu());
        }
        return true;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Simple 27-slot menu: border, info button, close button. */
    private static final class DemoMenu extends VirtualMenu {
        DemoMenu() {
            super(Component.text("Packet Chest (dupe-proof)"), MenuSize.ROW_3);
            fillBorder(MenuItem.just(named(Material.GRAY_STAINED_GLASS_PANE, " ")));
            setItem(13, MenuItem.of(named(Material.DIAMOND, "Click me"),
                    ctx -> ctx.player().sendMessage("Clicked slot 13 with " + ctx.clickType())));
            setItem(26, MenuItem.of(named(Material.BARRIER, "Close"), ClickHandler.close()));
        }
    }

    /** Paginated menu over 40 visual items. */
    private static final class DemoPagedMenu extends PaginatedMenu {
        DemoPagedMenu() {
            super(Component.text("Paged packets"), MenuSize.ROW_4);
            List<MenuItem> content = new ArrayList<>();
            for (int i = 1; i <= 40; i++) {
                int number = i;
                content.add(MenuItem.of(named(Material.PAPER, "Entry #" + number),
                        ctx -> ctx.player().sendMessage("Picked entry " + number)));
            }
            setContent(content);
        }
    }
}
