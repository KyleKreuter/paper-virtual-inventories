package de.kyle.virtualinventories.serialize;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the human-readable YAML format into a {@link MenuDefinition}.
 *
 * <pre>{@code
 * rows: 3
 * title: "<gold>Shop"
 * slots:
 *   10:
 *     material: DIAMOND
 *     amount: 1
 *     name: "<aqua>Diamond <gray>(%balance%)"
 *     lore:
 *       - "<gray>Price: <green>%price%"
 *     flags: [HIDE_ATTRIBUTES]
 *     custom_model_data: 5
 *     action: buy_diamond
 * }</pre>
 *
 * <p>{@code amount} also accepts a single placeholder ({@code amount: "%kills%"}).
 * Anvils use {@code type: ANVIL} (fixed window, {@code rows} ignored) plus an
 * optional {@code deposit: [0, 1]} list of player-fillable slots:</p>
 * <pre>{@code
 * type: ANVIL
 * title: "<gold>Rename item"
 * deposit: [0, 1]
 * slots:
 *   0: {material: AIR}
 *   1: {material: AIR}
 *   2:
 *     material: NAME_TAG
 *     name: "<green>Done"
 *     action: submit_name
 * }</pre>
 *
 * <p>Only structural mapping happens here, semantic validation is the compiler's job.</p>
 */
public final class DefinitionParser {

    private DefinitionParser() {
    }

    public static MenuDefinition parse(String menuId, String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new MenuCompileException("[" + menuId + "] invalid YAML: " + e.getMessage(), e);
        }

        if (cfg.isString("id") && !cfg.getString("id", "").equals(menuId)) {
            throw MenuCompileException.at(menuId, "id", "id field '" + cfg.getString("id")
                    + "' does not match registered id '" + menuId + "'");
        }
        Object rowsRaw = cfg.get("rows");
        String type = cfg.isString("type") ? cfg.getString("type") : "CHEST";
        int rows;
        if (rowsRaw instanceof Number number) {
            rows = number.intValue();
        } else if (rowsRaw == null && !"CHEST".equals(type.strip().toUpperCase(java.util.Locale.ROOT))) {
            rows = 1; // fixed-size windows (anvil, hopper, ...) omit 'rows'
        } else {
            throw MenuCompileException.at(menuId, "rows", "missing or not a number (expected 1-6)");
        }
        String title = cfg.isString("title") ? cfg.getString("title") : "";
        List<Integer> deposit = new ArrayList<>(cfg.getIntegerList("deposit"));

        ConfigurationSection slotsSection = cfg.getConfigurationSection("slots");
        Map<Integer, MenuDefinition.SlotDefinition> slots = new LinkedHashMap<>();
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    throw MenuCompileException.at(menuId, "slots." + key, "slot key is not a number");
                }
                ConfigurationSection sec = slotsSection.getConfigurationSection(key);
                if (sec == null) {
                    throw MenuCompileException.at(menuId, "slots." + key, "slot is not a mapping");
                }
                if (slots.put(slot, parseSlot(menuId, slot, sec)) != null) {
                    throw MenuCompileException.at(menuId, "slots." + key, "duplicate slot");
                }
            }
        }
        return new MenuDefinition(menuId, rows, title, slots, type, deposit);
    }

    private static MenuDefinition.SlotDefinition parseSlot(String menuId, int slot, ConfigurationSection sec) {
        String where = "slots." + slot;
        String material = sec.isString("material") ? sec.getString("material") : null;
        if (material == null || material.isBlank()) {
            throw MenuCompileException.at(menuId, where, "missing required 'material'");
        }

        int amount = 1;
        String amountPlaceholder = null;
        Object amountRaw = sec.get("amount", 1);
        if (amountRaw instanceof Number number) {
            amount = number.intValue();
        } else if (amountRaw instanceof String text) {
            List<Segment> segs = Segments.parse(text);
            if (segs.size() == 1 && segs.get(0) instanceof Segment.Placeholder placeholder) {
                amountPlaceholder = placeholder.key();
            } else {
                throw MenuCompileException.at(menuId, where,
                        "'amount' as string must be a single placeholder like \"%kills%\"");
            }
        } else {
            throw MenuCompileException.at(menuId, where, "'amount' must be a number or a placeholder");
        }

        String name = sec.isString("name") ? sec.getString("name") : null;
        List<String> lore = new ArrayList<>(sec.getStringList("lore"));
        List<String> flags = new ArrayList<>(sec.getStringList("flags"));
        Object cmdRaw = sec.get("custom_model_data");
        Integer customModelData = cmdRaw instanceof Number number ? number.intValue() : null;
        String action = sec.isString("action") ? sec.getString("action") : null;

        MenuDefinition.ItemTemplate template = new MenuDefinition.ItemTemplate(
                material.strip(), amount, amountPlaceholder, name, lore, flags, customModelData);
        return new MenuDefinition.SlotDefinition(template, action);
    }
}
