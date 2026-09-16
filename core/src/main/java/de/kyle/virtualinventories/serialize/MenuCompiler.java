package de.kyle.virtualinventories.serialize;

import de.kyle.virtualinventories.menu.WindowType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates a {@link MenuDefinition} and compiles it into a {@link CompiledForm}:
 * all texts are parsed into {@link Segment}s once, the full placeholder key set
 * is collected, slots are sorted. Pure logic, no Bukkit dependency.
 */
public final class MenuCompiler {

    private MenuCompiler() {
    }

    public static CompiledForm compile(MenuDefinition definition, String sourceShaHex) {
        String menuId = definition.id();
        requireId(menuId);
        WindowType windowType;
        String type = definition.type();
        if ("CHEST".equals(type)) {
            if (definition.rows() < 1 || definition.rows() > 6) {
                throw MenuCompileException.at(menuId, "rows",
                        "must be 1-6, got " + definition.rows());
            }
            windowType = WindowType.chest(definition.rows());
        } else {
            try {
                windowType = WindowType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw MenuCompileException.at(menuId, "type",
                        "unknown window type '" + type + "' (expected CHEST, ANVIL, "
                                + "GENERIC_3X3, CRAFTER_3X3, HOPPER, SHULKER_BOX, FURNACE, "
                                + "BLAST_FURNACE, SMOKER, BREWING_STAND, MERCHANT, "
                                + "ENCHANTMENT, STONECUTTER or LOOM)");
            }
            if (definition.rows() != 1) {
                throw MenuCompileException.at(menuId, "rows",
                        windowType + " has a fixed size, omit 'rows'");
            }
        }
        List<Integer> deposit = List.copyOf(definition.depositSlots());
        if (!deposit.isEmpty() && !windowType.allowsDeposit()) {
            throw MenuCompileException.at(menuId, "deposit",
                    "'deposit' is only supported for ANVIL, FURNACE, BLAST_FURNACE, "
                            + "SMOKER, BREWING_STAND, ENCHANTMENT, MERCHANT, STONECUTTER and LOOM");
        }
        for (int slot : deposit) {
            if (slot < 0 || slot >= windowType.slots()) {
                throw MenuCompileException.at(menuId, "deposit",
                        "slot " + slot + " out of bounds for " + windowType + " (0-"
                                + (windowType.slots() - 1) + ")");
            }
        }
        List<Integer> output = List.copyOf(definition.outputSlots());
        for (int slot : output) {
            if (slot < 0 || slot >= windowType.slots()) {
                throw MenuCompileException.at(menuId, "output",
                        "slot " + slot + " out of bounds for " + windowType + " (0-"
                                + (windowType.slots() - 1) + ")");
            }
            if (deposit.contains(slot)) {
                throw MenuCompileException.at(menuId, "output",
                        "slot " + slot + " is already a deposit slot");
            }
        }
        Map<Integer, Integer> containerData = Map.copyOf(definition.containerData());
        for (Map.Entry<Integer, Integer> entry : containerData.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() > 255) {
                throw MenuCompileException.at(menuId, "data",
                        "property id " + entry.getKey() + " out of range (0-255)");
            }
        }
        Map<Integer, String> buttons = Map.copyOf(definition.buttonActions());
        for (Map.Entry<Integer, String> entry : buttons.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() > 255) {
                throw MenuCompileException.at(menuId, "buttons",
                        "button id " + entry.getKey() + " out of range (0-255)");
            }
            if (entry.getValue() == null || entry.getValue().isBlank()
                    || !entry.getValue().matches("[A-Za-z0-9_.-]+")) {
                throw MenuCompileException.at(menuId, "buttons",
                        "invalid action id '" + entry.getValue() + "'");
            }
        }
        List<CompiledForm.CompiledTrade> trades = new ArrayList<>();
        for (MenuDefinition.TradeDefinition trade : definition.trades()) {
            trades.add(compileTrade(menuId, trade));
        }
        if (!trades.isEmpty() && !"MERCHANT".equals(windowType.name())) {
            throw MenuCompileException.at(menuId, "trades",
                    "'trades' is only supported for type MERCHANT");
        }
        String title = definition.title() == null ? "" : definition.title();
        int maxSlot = "CHEST".equals(type) ? definition.rows() * 9 : windowType.slots();

        List<CompiledForm.CompiledSlot> slots = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        keys.addAll(Segments.keys(Segments.parse(title)));

        List<Integer> ordered = new ArrayList<>(definition.slots().keySet());
        ordered.sort(Integer::compareTo);
        for (int slot : ordered) {
            if (slot < 0 || slot >= maxSlot) {
                throw MenuCompileException.at(menuId, "slots." + slot,
                        "out of bounds for " + windowType + " (0-" + (maxSlot - 1) + ")");
            }
            MenuDefinition.SlotDefinition slotDef = definition.slots().get(slot);
            slots.add(compileSlot(menuId, slot, slotDef, keys,
                    deposit.contains(slot), output.contains(slot)));
        }
        return new CompiledForm(menuId, definition.rows(), windowType.name(), deposit,
                Segments.parse(title), List.copyOf(slots), Set.copyOf(keys), sourceShaHex,
                containerData, output, buttons, List.copyOf(trades));
    }

    private static CompiledForm.CompiledTrade compileTrade(
            String menuId, MenuDefinition.TradeDefinition trade) {
        requireMaterial(menuId, "trades", trade.buyA());
        requireCount(menuId, "trades", trade.buyACount());
        if (trade.buyB() != null) {
            requireMaterial(menuId, "trades", trade.buyB());
            if (trade.buyBCount() == null) {
                throw MenuCompileException.at(menuId, "trades",
                        "trade with 'buy_b' needs 'buy_b_count'");
            }
            requireCount(menuId, "trades", trade.buyBCount());
        }
        requireMaterial(menuId, "trades", trade.result());
        requireCount(menuId, "trades", trade.resultCount());
        if (trade.maxUses() < 1 || trade.maxUses() > 1_000_000) {
            throw MenuCompileException.at(menuId, "trades",
                    "'max_uses' must be >= 1, got " + trade.maxUses());
        }
        return new CompiledForm.CompiledTrade(trade.buyA(), trade.buyACount(),
                trade.buyB(), trade.buyBCount(), trade.result(), trade.resultCount(),
                trade.maxUses());
    }

    private static void requireMaterial(String menuId, String where, String material) {
        if (material == null || material.isBlank()) {
            throw MenuCompileException.at(menuId, where, "trade needs a material name");
        }
    }

    private static void requireCount(String menuId, String where, int count) {
        if (count < 1 || count > 64) {
            throw MenuCompileException.at(menuId, where,
                    "trade counts must be 1-64, got " + count);
        }
    }

    private static CompiledForm.CompiledSlot compileSlot(
            String menuId, int slot, MenuDefinition.SlotDefinition slotDef, Set<String> keys,
            boolean isDeposit, boolean isOutput) {
        String where = "slots." + slot;
        MenuDefinition.ItemTemplate item = slotDef.item();
        if (item.amount() < 1 || item.amount() > 99) {
            throw MenuCompileException.at(menuId, where, "'amount' must be 1-99, got " + item.amount());
        }
        String action = slotDef.action() == null || slotDef.action().isBlank() ? null : slotDef.action();
        if (action != null && !action.matches("[A-Za-z0-9_.-]+")) {
            throw MenuCompileException.at(menuId, where, "invalid action id '" + slotDef.action() + "'");
        }
        if (item.itemRef() != null) {
            return compileReference(menuId, slot, item, action, isDeposit, isOutput);
        }
        if (item.material() == null || item.material().isBlank()) {
            throw MenuCompileException.at(menuId, where, "missing required 'material'");
        }
        if (isDeposit || isOutput) {
            if (!"AIR".equalsIgnoreCase(item.material())) {
                throw MenuCompileException.at(menuId, where,
                        (isDeposit ? "deposit" : "output")
                                + " slots must use material AIR (player items go here)");
            }
        }
        // Deposit slots are pure item conduits. Output slots may define an
        // 'action' that owns the take (trades, machines, ...); output clicks
        // without an action hand over the displayed stack by default.
        if (isDeposit && slotDef.action() != null && !slotDef.action().isBlank()) {
            throw MenuCompileException.at(menuId, where, "deposit slots must not define 'action'");
        }
        if (item.amountPlaceholder() != null && !Segments.isKey(item.amountPlaceholder())) {
            throw MenuCompileException.at(menuId, where, "invalid amount placeholder '" + item.amountPlaceholder() + "'");
        }
        if (item.amountPlaceholder() != null) {
            keys.add(item.amountPlaceholder());
        }

        List<Segment> name = item.name() == null ? List.of() : Segments.parse(item.name());
        keys.addAll(Segments.keys(name));
        List<List<Segment>> lore = new ArrayList<>();
        for (String line : item.lore()) {
            List<Segment> parsed = Segments.parse(line);
            keys.addAll(Segments.keys(parsed));
            lore.add(parsed);
        }
        Map<String, String> seenFlags = new LinkedHashMap<>();
        for (String flag : item.flags()) {
            seenFlags.put(flag.strip().toUpperCase(java.util.Locale.ROOT), flag);
        }
        return new CompiledForm.CompiledSlot(slot, item.material(), item.amount(), item.amountPlaceholder(),
                name, List.copyOf(lore), List.copyOf(seenFlags.keySet()), item.customModelData(),
                action, null);
    }

    /**
     * Compiles a {@code ref:} slot: the id is validated and stored, the stack
     * itself is resolved from the {@code ItemProvider} at menu load. Only
     * {@code amount} may override the stored stack size; every other template
     * field is rejected so the reference stays the single source of truth.
     */
    private static CompiledForm.CompiledSlot compileReference(
            String menuId, int slot, MenuDefinition.ItemTemplate item, String action,
            boolean isDeposit, boolean isOutput) {
        String where = "slots." + slot;
        String ref = item.itemRef().strip();
        if (!ref.matches("[A-Za-z0-9_.-]+")) {
            throw MenuCompileException.at(menuId, where, "invalid item ref id '" + item.itemRef() + "'");
        }
        if (isDeposit || isOutput) {
            throw MenuCompileException.at(menuId, where,
                    "'ref' is not allowed on " + (isDeposit ? "deposit" : "output")
                            + " slots (player items go here)");
        }
        if (item.material() != null && !item.material().isBlank()) {
            throw MenuCompileException.at(menuId, where, "'ref' cannot be combined with 'material'");
        }
        if (item.amountPlaceholder() != null || item.name() != null || !item.lore().isEmpty()
                || !item.flags().isEmpty() || item.customModelData() != null) {
            throw MenuCompileException.at(menuId, where,
                    "'ref' cannot be combined with name/lore/flags/custom_model_data/"
                            + "amount-placeholders (only 'amount' may override the stored size)");
        }
        return new CompiledForm.CompiledSlot(slot, null, item.amount(), null,
                List.of(), List.of(), List.of(), null, action, ref);
    }

    private static void requireId(String menuId) {
        if (menuId == null || menuId.isBlank() || !menuId.matches("[A-Za-z0-9_.-]+")) {
            throw new MenuCompileException("Invalid menu id '" + menuId + "' (expected [A-Za-z0-9_.-]+)");
        }
    }

    /** SHA-256 hex of a definition source, stored in the blob for staleness checks. */
    public static String sha256Hex(String source) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
