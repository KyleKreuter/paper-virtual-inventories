package de.kyle.virtualinventories.demo;

import de.kyle.virtualinventories.VirtualInventories;
import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.provider.MenuHooks;
import de.kyle.virtualinventories.provider.MenuProvider;
import de.kyle.virtualinventories.session.MenuSession;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demo plugin for the compiled-menu v1 API: menus come from YAML files,
 * placeholders and click actions are registered in code.
 * Open with /vmenu (dynamic values), /vpaged (multi-page YAML menus),
 * /vname (anvil text input + deposit slot), /vmenu furnace|smoker (live
 * container-data ticker), /vmenu brewing|merchant|enchant|stonecutter|loom
 * or /vreload (recompile YAML).
 */
public final class DemoPlugin extends JavaPlugin implements CommandExecutor {

    private MenuProvider menus;
    private final Map<UUID, Integer> furnaceCook = new HashMap<>();

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
        menus.action("confirm_name", ctx -> {
            Player viewer = ctx.player();
            String text = ctx.session().view().text();
            viewer.sendMessage("Confirmed name: " + (text.isEmpty() ? "(empty)" : text));
            VirtualInventories.api().close(viewer);
        });
        menus.action("enchant_1", ctx -> enchant(ctx.player(), ctx.session(), 1));
        menus.action("enchant_2", ctx -> enchant(ctx.player(), ctx.session(), 2));
        menus.action("enchant_3", ctx -> enchant(ctx.player(), ctx.session(), 3));

        menus.hooks("demo", new MenuHooks(
                (player, session) -> player.sendMessage("Welcome to the compiled demo menu."),
                null));

        saveResource("menus/demo.yml", false);
        saveResource("menus/paged1.yml", false);
        saveResource("menus/paged2.yml", false);
        saveResource("menus/paged3.yml", false);
        saveResource("menus/name.yml", false);
        saveResource("menus/hopper.yml", false);
        saveResource("menus/shulker.yml", false);
        saveResource("menus/dispenser.yml", false);
        saveResource("menus/crafter.yml", false);
        saveResource("menus/furnace.yml", false);
        saveResource("menus/smoker.yml", false);
        saveResource("menus/brewing.yml", false);
        saveResource("menus/merchant.yml", false);
        saveResource("menus/enchant.yml", false);
        saveResource("menus/stonecutter.yml", false);
        saveResource("menus/loom.yml", false);
        menus.loadDirectory();

        new BukkitRunnable() {
            @Override
            public void run() {
                tickMachines();
            }
        }.runTaskTimer(this, 10L, 10L);

        getCommand("vmenu").setExecutor(this);
        getCommand("vpaged").setExecutor(this);
        getCommand("vname").setExecutor(this);
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
                case "vname" -> menus.open(player, "name");
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

    private void enchant(Player viewer, MenuSession session, int level) {
        ItemStack tool = session.deposit(0);
        ItemStack lapis = session.deposit(1);
        if (tool == null || tool.getType().isAir()
                || lapis == null || lapis.getType() != Material.LAPIS_LAZULI
                || lapis.getAmount() < level) {
            viewer.sendMessage("Put a tool in the left slot and at least "
                    + level + " lapis in the right slot first.");
            return;
        }
        tool.addUnsafeEnchantment(Enchantment.SHARPNESS, level);
        lapis.setAmount(lapis.getAmount() - level);
        session.setDeposit(0, tool);
        session.setDeposit(1, lapis.getAmount() > 0 ? lapis : null);
        viewer.sendMessage("Enchanted with Sharpness " + level + ".");
    }

    private void tickMachines() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MenuSession session = VirtualInventories.api().sessions().get(player);
            if (session == null) {
                furnaceCook.remove(player.getUniqueId());
                continue;
            }
            switch (session.menu().id()) {
                case "furnace", "smoker" -> tickFurnace(player, session);
                case "stonecutter" -> tickCutter(session);
                case "loom" -> tickLoom(session);
                default -> furnaceCook.remove(player.getUniqueId());
            }
        }
    }

    private void tickFurnace(Player player, MenuSession session) {
        int cook = (furnaceCook.getOrDefault(player.getUniqueId(), 0) + 10) % 210;
        furnaceCook.put(player.getUniqueId(), cook);
        session.setContainerData(0, 200);
        session.setContainerData(1, 200);
        session.setContainerData(2, cook);
        session.setContainerData(3, 200);
        if (cook != 0) {
            return;
        }
        ItemStack input = session.deposit(0);
        if (input == null || input.getType().isAir() || input.getAmount() <= 0) {
            return;
        }
        Material result = smeltResult(input.getType());
        if (result == null) {
            return;
        }
        input.setAmount(input.getAmount() - 1);
        session.setDeposit(0, input.getAmount() > 0 ? input : null);
        ItemStack current = session.snapshot(2);
        if (current == null || current.getType().isAir()) {
            session.setSlot(2, new ItemStack(result));
        } else if (current.getType() == result && current.getAmount() < 64) {
            current.setAmount(current.getAmount() + 1);
            session.setSlot(2, current);
        }
    }

    private static Material smeltResult(Material input) {
        return switch (input) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            default -> null;
        };
    }

    private void tickCutter(MenuSession session) {
        ItemStack input = session.deposit(0);
        if (input == null || input.getType() != Material.COBBLESTONE || input.getAmount() <= 0) {
            return;
        }
        ItemStack current = session.snapshot(1);
        if (current != null && !current.getType().isAir()
                && (current.getType() != Material.STONE_BRICKS || current.getAmount() > 60)) {
            return;
        }
        input.setAmount(input.getAmount() - 1);
        session.setDeposit(0, input.getAmount() > 0 ? input : null);
        int amount = (current == null || current.getType().isAir()) ? 0 : current.getAmount();
        session.setSlot(1, new ItemStack(Material.STONE_BRICKS, amount + 4));
    }

    private void tickLoom(MenuSession session) {
        ItemStack banner = session.deposit(0);
        ItemStack dye = session.deposit(1);
        if (banner == null || banner.getType() != Material.WHITE_BANNER || banner.getAmount() <= 0
                || dye == null || dye.getType() != Material.RED_DYE || dye.getAmount() <= 0) {
            return;
        }
        ItemStack current = session.snapshot(3);
        if (current != null && !current.getType().isAir()) {
            return;
        }
        banner.setAmount(banner.getAmount() - 1);
        dye.setAmount(dye.getAmount() - 1);
        session.setDeposit(0, banner.getAmount() > 0 ? banner : null);
        session.setDeposit(1, dye.getAmount() > 0 ? dye : null);
        // Banner color lives in the material: white + red dye -> red banner.
        session.setSlot(3, new ItemStack(Material.RED_BANNER));
    }

    private static String itemName(ItemStack clicked) {
        if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        }
        return "?";
    }
}
