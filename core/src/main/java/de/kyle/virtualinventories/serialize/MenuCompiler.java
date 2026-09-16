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
        if ("ANVIL".equals(type)) {
            windowType = WindowType.ANVIL;
        } else if ("CHEST".equals(type)) {
            if (definition.rows() < 1 || definition.rows() > 6) {
                throw MenuCompileException.at(menuId, "rows",
                        "must be 1-6, got " + definition.rows());
            }
            windowType = WindowType.chest(definition.rows());
        } else {
            throw MenuCompileException.at(menuId, "type",
                    "unknown window type '" + type + "' (expected CHEST or ANVIL)");
        }
        if (windowType.isAnvil() && definition.rows() != 1) {
            throw MenuCompileException.at(menuId, "rows",
                    "anvil menus are single-row (slots 0-2), omit 'rows'");
        }
        List<Integer> deposit = List.copyOf(definition.depositSlots());
        if (!deposit.isEmpty() && !windowType.isAnvil()) {
            throw MenuCompileException.at(menuId, "deposit",
                    "'deposit' is only supported for type ANVIL");
        }
        for (int slot : deposit) {
            if (slot < 0 || slot >= windowType.slots()) {
                throw MenuCompileException.at(menuId, "deposit",
                        "slot " + slot + " out of bounds for " + windowType + " (0-"
                                + (windowType.slots() - 1) + ")");
            }
        }
        String title = definition.title() == null ? "" : definition.title();
        int maxSlot = windowType.isAnvil() ? windowType.slots() : definition.rows() * 9;

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
            slots.add(compileSlot(menuId, slot, slotDef, keys, deposit.contains(slot)));
        }
        return new CompiledForm(menuId, definition.rows(), windowType.name(), deposit,
                Segments.parse(title), List.copyOf(slots), Set.copyOf(keys), sourceShaHex);
    }

    private static CompiledForm.CompiledSlot compileSlot(
            String menuId, int slot, MenuDefinition.SlotDefinition slotDef, Set<String> keys,
            boolean isDeposit) {
        String where = "slots." + slot;
        MenuDefinition.ItemTemplate item = slotDef.item();
        if (isDeposit) {
            if (!"AIR".equalsIgnoreCase(item.material())) {
                throw MenuCompileException.at(menuId, where,
                        "deposit slots must use material AIR (player items go here)");
            }
            if (slotDef.action() != null && !slotDef.action().isBlank()) {
                throw MenuCompileException.at(menuId, where,
                        "deposit slots must not define 'action'");
            }
        }
        if (item.amount() < 1 || item.amount() > 99) {
            throw MenuCompileException.at(menuId, where, "'amount' must be 1-99, got " + item.amount());
        }
        if (item.amountPlaceholder() != null && !Segments.isKey(item.amountPlaceholder())) {
            throw MenuCompileException.at(menuId, where, "invalid amount placeholder '" + item.amountPlaceholder() + "'");
        }
        if (slotDef.action() != null && !slotDef.action().isBlank()
                && !slotDef.action().matches("[A-Za-z0-9_.-]+")) {
            throw MenuCompileException.at(menuId, where, "invalid action id '" + slotDef.action() + "'");
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
                slotDef.action() == null || slotDef.action().isBlank() ? null : slotDef.action());
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
