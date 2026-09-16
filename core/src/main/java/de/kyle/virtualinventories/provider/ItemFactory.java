package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.serialize.MenuCompileException;
import de.kyle.virtualinventories.serialize.Segments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Materializes items from compiled templates. Static slots are built once at
 * menu load, dynamic slots are rebuilt per open from the resolved scope.
 */
final class ItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ItemFactory() {
    }

    static ItemStack build(String menuId, int slot, String materialKey, int amount,
                           List<de.kyle.virtualinventories.serialize.Segment> name,
                           List<List<de.kyle.virtualinventories.serialize.Segment>> lore,
                           List<String> flags, Integer customModelData, Map<String, String> scope) {
        Material material = Material.matchMaterial(materialKey);
        if (material == null || material.isAir()) {
            throw MenuCompileException.at(menuId, "slots." + slot, "unknown material '" + materialKey + "'");
        }
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(99, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!name.isEmpty()) {
            meta.displayName(component(name, scope));
        }
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (List<de.kyle.virtualinventories.serialize.Segment> line : lore) {
                lines.add(component(line, scope));
            }
            meta.lore(lines);
        }
        for (String flag : flags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag));
            } catch (IllegalArgumentException e) {
                throw MenuCompileException.at(menuId, "slots." + slot, "unknown item flag '" + flag + "'");
            }
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component component(List<de.kyle.virtualinventories.serialize.Segment> segments,
                                       Map<String, String> scope) {
        return MINI_MESSAGE.deserialize(Segments.render(segments, scope));
    }
}
