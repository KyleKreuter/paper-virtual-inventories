package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCompilerTest {

    private static MenuDefinition definition(int rows, Map<Integer, MenuDefinition.SlotDefinition> slots) {
        return new MenuDefinition("test", rows, "Title %player%", slots);
    }

    private static MenuDefinition.SlotDefinition slot(String material) {
        return new MenuDefinition.SlotDefinition(MenuDefinition.ItemTemplate.simple(material, "Name"), null);
    }

    @Test
    void collectsPlaceholderKeys() {
        MenuDefinition.ItemTemplate item = new MenuDefinition.ItemTemplate(
                "DIAMOND", 1, "kills", "Hi %player%", List.of("%balance%"), List.of(), null);
        MenuDefinition def = definition(3, Map.of(10, new MenuDefinition.SlotDefinition(item, "buy")));
        CompiledForm form = MenuCompiler.compile(def, "0".repeat(64));
        assertEquals(Set.of("player", "kills", "balance"), form.placeholderKeys());
        assertTrue(form.slots().get(0).isDynamic());
    }

    @Test
    void staticSlotDetected() {
        CompiledForm form = MenuCompiler.compile(
                new MenuDefinition("s", 1, "Plain", Map.of(0, slot("STONE"))), "0".repeat(64));
        assertFalse(form.slots().get(0).isDynamic());
        assertTrue(form.placeholderKeys().isEmpty());
    }

    @Test
    void rowsOutOfRangeFail() {
        assertThrows(MenuCompileException.class,
                () -> MenuCompiler.compile(definition(0, Map.of()), "0".repeat(64)));
        assertThrows(MenuCompileException.class,
                () -> MenuCompiler.compile(definition(7, Map.of()), "0".repeat(64)));
    }

    @Test
    void slotOutOfBoundsFails() {
        assertThrows(MenuCompileException.class,
                () -> MenuCompiler.compile(definition(1, Map.of(9, slot("STONE"))), "0".repeat(64)));
    }

    @Test
    void badMenuIdFails() {
        MenuDefinition def = new MenuDefinition("has space", 1, "", Map.of());
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void amountOutOfRangeFails() {
        MenuDefinition.ItemTemplate item = new MenuDefinition.ItemTemplate(
                "STONE", 0, null, null, List.of(), List.of(), null);
        MenuDefinition def = definition(1, Map.of(0, new MenuDefinition.SlotDefinition(item, null)));
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void badActionIdFails() {
        MenuDefinition.SlotDefinition slotDef =
                new MenuDefinition.SlotDefinition(MenuDefinition.ItemTemplate.simple("STONE", "x"), "has space");
        assertThrows(MenuCompileException.class,
                () -> MenuCompiler.compile(definition(1, Map.of(0, slotDef)), "0".repeat(64)));
    }

    private static MenuDefinition anvilDef(Map<Integer, MenuDefinition.SlotDefinition> slots,
            List<Integer> deposit) {
        return new MenuDefinition("anvil", 1, "Name it", slots, "ANVIL", deposit);
    }

    private static MenuDefinition.SlotDefinition airSlot() {
        return new MenuDefinition.SlotDefinition(
                new MenuDefinition.ItemTemplate("AIR", 1, null, null, List.of(), List.of(), null), null);
    }

    @Test
    void validAnvilCompiles() {
        MenuDefinition def = anvilDef(Map.of(
                0, airSlot(),
                1, slot("PAPER"),
                2, new MenuDefinition.SlotDefinition(
                        MenuDefinition.ItemTemplate.simple("NAME_TAG", "OK"), "confirm")), List.of(0));
        CompiledForm form = MenuCompiler.compile(def, "0".repeat(64));
        assertEquals("ANVIL", form.windowType());
        assertEquals(List.of(0), form.depositSlots());
        assertTrue(form.isAnvil());
    }

    @Test
    void anvilRowsMustBeOne() {
        MenuDefinition def = new MenuDefinition("a", 2, "T", Map.of(), "ANVIL", List.of());
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void anvilSlotOutOfBoundsFails() {
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(
                anvilDef(Map.of(3, slot("STONE")), List.of()), "0".repeat(64)));
    }

    @Test
    void depositMustBeAir() {
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(
                anvilDef(Map.of(0, slot("STONE")), List.of(0)), "0".repeat(64)));
    }

    @Test
    void depositMustNotHaveAction() {
        MenuDefinition.SlotDefinition withAction = new MenuDefinition.SlotDefinition(
                new MenuDefinition.ItemTemplate("AIR", 1, null, null, List.of(), List.of(), null), "take");
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(
                anvilDef(Map.of(0, withAction), List.of(0)), "0".repeat(64)));
    }

    @Test
    void chestMustNotHaveDeposit() {
        MenuDefinition def = new MenuDefinition("c", 1, "T",
                Map.of(0, airSlot()), "CHEST", List.of(0));
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void unknownWindowTypeFails() {
        MenuDefinition def = new MenuDefinition("u", 1, "T", Map.of(), "FURNACE", List.of());
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void validHopperCompiles() {
        MenuDefinition def = new MenuDefinition("h", 1, "Hi %player%",
                Map.of(0, slot("HOPPER"), 4, slot("STONE")), "HOPPER", List.of());
        CompiledForm form = MenuCompiler.compile(def, "0".repeat(64));
        assertEquals("HOPPER", form.windowType());
        assertFalse(form.isAnvil());
    }

    @Test
    void fixedSizeTypeRejectsRows() {
        MenuDefinition def = new MenuDefinition("h", 2, "T", Map.of(), "HOPPER", List.of());
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void hopperSlotOutOfBoundsFails() {
        MenuDefinition def = new MenuDefinition("h", 1, "T",
                Map.of(5, slot("STONE")), "HOPPER", List.of());
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }

    @Test
    void shulkerMaxSlotCompiles() {
        MenuDefinition def = new MenuDefinition("s", 1, "T",
                Map.of(26, slot("STONE")), "SHULKER_BOX", List.of());
        CompiledForm form = MenuCompiler.compile(def, "0".repeat(64));
        assertEquals("SHULKER_BOX", form.windowType());
    }

    @Test
    void fixedSizeTypeRejectsDeposit() {
        MenuDefinition def = new MenuDefinition("h", 1, "T",
                Map.of(0, airSlot()), "HOPPER", List.of(0));
        assertThrows(MenuCompileException.class, () -> MenuCompiler.compile(def, "0".repeat(64)));
    }
}
