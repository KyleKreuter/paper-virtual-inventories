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
}
