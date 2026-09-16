package de.kyle.virtualinventories.demo;

import de.kyle.virtualinventories.VirtualInventories;
import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.provider.MenuHooks;
import de.kyle.virtualinventories.provider.MenuProvider;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Demo plugin for the compiled-menu v1 API: menus come from YAML files,
 * placeholders and click actions are registered in code.
 * Open with /vmenu (dynamic values), /vpaged (multi-page YAML menus) or
 * /vreload (recompile YAML + refresh *.vmenu.gz blobs).
 */
public final class DemoPlugin extends JavaPlugin implements CommandExecutor {

    private MenuProvider menus;

    @Override
    public void onEnable() {
        VirtualInventories.init(this);
        menus = VirtualInventories.api().menus();

        menus.placeholders().register("balance", player -> String.valueOf(player.getLevel() * 137L));
        menus.placeholders().register("kills",
                player -> String.valueOf(player.getStatistic(Statistic.MOB_KILLS)));
        menus.placeholders().register("price", player -> "64");

        menus.action("close", ClickHandler.close());
        menus.action("demo_click", ctx ->
                ctx.player().sendMessage("Clicked slot " + ctx.slot() + " with " + ctx.clickType()));
        menus.action("demo_bump", ctx -> {
            Player viewer = ctx.player();
            viewer.setLevel(viewer.getLevel() + 1);
            menus.refreshDynamic(viewer);
            viewer.sendMessage("Balance bumped, menu refreshed.");
        });
        menus.action("pick", ctx -> ctx.player().sendMessage("Picked " + itemName(ctx.clickedItem())));
        menus.action("open_paged1", ctx -> switchPage(ctx.player(), "paged1"));
        menus.action("open_paged2", ctx -> switchPage(ctx.player(), "paged2"));
        menus.action("open_paged3", ctx -> switchPage(ctx.player(), "paged3"));

        menus.hooks("demo", new MenuHooks(
                (player, session) -> player.sendMessage("Welcome to the compiled demo menu."),
                null));

        saveResource("menus/demo.yml", false);
        saveResource("menus/paged1.yml", false);
        saveResource("menus/paged2.yml", false);
        saveResource("menus/paged3.yml", false);
        menus.loadDirectory();

        getCommand("vmenu").setExecutor(this);
        getCommand("vpaged").setExecutor(this);
        getCommand("vreload").setExecutor(this);
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
        try {
            switch (command.getName().toLowerCase()) {
                case "vpaged" -> menus.open(player, "paged1");
                case "vreload" -> {
                    menus.reload();
                    player.sendMessage("Reloaded " + menus.menuIds().size()
                            + " menus: " + String.join(", ", menus.menuIds()));
                }
                default -> menus.open(player, args.length > 0 ? args[0] : "demo");
            }
        } catch (RuntimeException e) {
            player.sendMessage("Menu error: " + e.getMessage());
        }
        return true;
    }

    private void switchPage(Player player, String menuId) {
        menus.switchTo(player, menuId);
    }

    private static String itemName(ItemStack clicked) {
        if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        }
        return "?";
    }
}
