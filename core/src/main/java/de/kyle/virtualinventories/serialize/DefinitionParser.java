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
        List<Integer> output = new ArrayList<>(cfg.getIntegerList("output"));

        Map<Integer, Integer> containerData = new LinkedHashMap<>();
        ConfigurationSection dataSection = cfg.getConfigurationSection("data");
        if (dataSection != null) {
            for (String key : dataSection.getKeys(false)) {
                int property;
                try {
                    property = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    throw MenuCompileException.at(menuId, "data." + key, "property id is not a number");
                }
                Object valueRaw = dataSection.get(key);
                if (!(valueRaw instanceof Number number)) {
                    throw MenuCompileException.at(menuId, "data." + key, "value is not a number");
                }
                containerData.put(property, number.intValue());
            }
        }

        Map<Integer, String> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonsSection = cfg.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            for (String key : buttonsSection.getKeys(false)) {
                int button;
                try {
                    button = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    throw MenuCompileException.at(menuId, "buttons." + key, "button id is not a number");
                }
                String action = buttonsSection.getString(key);
                if (action == null || action.isBlank()) {
                    throw MenuCompileException.at(menuId, "buttons." + key, "missing action id");
                }
                buttons.put(button, action.strip());
            }
        }

        List<MenuDefinition.TradeDefinition> trades = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("trades")) {
            trades.add(parseTrade(menuId, raw));
        }
        return new MenuDefinition(menuId, rows, title, slots, type, deposit,
                containerData, output, buttons, trades);
    }

    private static MenuDefinition.TradeDefinition parseTrade(String menuId, Map<?, ?> raw) {
        String buyA = stringField(menuId, raw, "buy_a", true);
        int buyACount = intField(menuId, raw, "buy_a_count", 1);
        Object buyBRaw = raw.get("buy_b");
        String buyB = buyBRaw == null ? null : stringField(menuId, raw, "buy_b", true);
        Integer buyBCount = buyBRaw == null ? null : intField(menuId, raw, "buy_b_count", 1);
        String result = stringField(menuId, raw, "result", true);
        int resultCount = intField(menuId, raw, "result_count", 1);
        int maxUses = intField(menuId, raw, "max_uses", 12);
        return new MenuDefinition.TradeDefinition(buyA, buyACount, buyB, buyBCount,
                result, resultCount, maxUses);
    }

    private static String stringField(String menuId, Map<?, ?> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null) {
            if (required) {
                throw MenuCompileException.at(menuId, "trades", "trade is missing '" + field + "'");
            }
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw MenuCompileException.at(menuId, "trades", "trade field '" + field + "' must be a string");
        }
        return text.strip();
    }

    private static int intField(String menuId, Map<?, ?> raw, String field, int fallback) {
        Object value = raw.get(field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw MenuCompileException.at(menuId, "trades", "trade field '" + field + "' must be a number");
        }
        return number.intValue();
    }

    private static MenuDefinition.SlotDefinition parseSlot(String menuId, int slot, ConfigurationSection sec) {
        String where = "slots." + slot;
        String ref = sec.isString("ref") ? sec.getString("ref").strip() : null;
        String material = sec.isString("material") ? sec.getString("material").strip() : null;
        if ((material == null || material.isBlank()) && (ref == null || ref.isBlank())) {
            throw MenuCompileException.at(menuId, where, "missing required 'material' (or 'ref')");
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
                material, amount, amountPlaceholder, name, lore, flags, customModelData,
                ref == null || ref.isBlank() ? null : ref);
        return new MenuDefinition.SlotDefinition(template, action);
    }
}
