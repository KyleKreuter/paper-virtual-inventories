package de.kyle.virtualinventories.serialize;

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
        if (definition.rows() < 1 || definition.rows() > 6) {
            throw MenuCompileException.at(menuId, "rows", "must be 1-6, got " + definition.rows());
        }
        String title = definition.title() == null ? "" : definition.title();
        int maxSlot = definition.rows() * 9;

        List<CompiledForm.CompiledSlot> slots = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        keys.addAll(Segments.keys(Segments.parse(title)));

        List<Integer> ordered = new ArrayList<>(definition.slots().keySet());
        ordered.sort(Integer::compareTo);
        for (int slot : ordered) {
            if (slot < 0 || slot >= maxSlot) {
                throw MenuCompileException.at(menuId, "slots." + slot,
                        "out of bounds for " + definition.rows() + " rows (0-" + (maxSlot - 1) + ")");
            }
            MenuDefinition.SlotDefinition slotDef = definition.slots().get(slot);
            slots.add(compileSlot(menuId, slot, slotDef, keys));
        }
        return new CompiledForm(menuId, definition.rows(), Segments.parse(title),
                List.copyOf(slots), Set.copyOf(keys), sourceShaHex);
    }

    private static CompiledForm.CompiledSlot compileSlot(
            String menuId, int slot, MenuDefinition.SlotDefinition slotDef, Set<String> keys) {
        String where = "slots." + slot;
        MenuDefinition.ItemTemplate item = slotDef.item();
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
