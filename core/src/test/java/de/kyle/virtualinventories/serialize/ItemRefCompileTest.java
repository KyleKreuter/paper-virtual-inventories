package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation rules for {@code ref:} slots (fully serialized item refs).
 * YAML-based so no constructor order can drift.
 */
class ItemRefCompileTest {

    private static CompiledForm compile(String menuId, String yaml) {
        return MenuCompiler.compile(DefinitionParser.parse(menuId, yaml), MenuCompiler.sha256Hex("test"));
    }

    private static final String REF_SLOT = """
            rows: 3
            title: "Refs"
            slots:
              12:
                ref: demo_crown
            """;

    @Test
    void refSlotCompilesAndStoresId() {
        CompiledForm form = compile("refs", REF_SLOT);
        assertEquals(1, form.slots().size());
        CompiledForm.CompiledSlot slot = form.slots().get(0);
        assertEquals(12, slot.slot());
        assertEquals("demo_crown", slot.itemRef());
        assertTrue(slot.isReference());
        assertNull(slot.material());
        assertTrue(form.placeholderKeys().isEmpty());
    }

    @Test
    void refWithAmountOverrideCompiles() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    ref: arrows
                    amount: 32
                """;
        CompiledForm.CompiledSlot slot = compile("refs", yaml).slots().get(0);
        assertEquals("arrows", slot.itemRef());
        assertEquals(32, slot.amount());
    }

    @Test
    void refCannotCombineWithMaterial() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    ref: crown
                    material: DIAMOND
                """;
        assertThrows(MenuCompileException.class, () -> compile("refs", yaml));
    }

    @Test
    void refCannotCombineWithNameOrLore() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    ref: crown
                    name: "Pretty"
                """;
        assertThrows(MenuCompileException.class, () -> compile("refs", yaml));

        String loreYaml = """
                rows: 1
                slots:
                  0:
                    ref: crown
                    lore: ["line"]
                """;
        assertThrows(MenuCompileException.class, () -> compile("refs", loreYaml));
    }

    @Test
    void refCannotCombineWithAmountPlaceholder() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    ref: crown
                    amount: "%kills%"
                """;
        assertThrows(MenuCompileException.class, () -> compile("refs", yaml));
    }

    @Test
    void refWithBadIdFails() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    ref: "has space"
                """;
        assertThrows(MenuCompileException.class, () -> compile("refs", yaml));
    }

    @Test
    void refOnDepositSlotFails() {
        String yaml = """
                type: ANVIL
                deposit: [0]
                slots:
                  0:
                    ref: crown
                  2:
                    material: NAME_TAG
                """;
        assertThrows(MenuCompileException.class, () -> compile("anvil", yaml));
    }

    @Test
    void refOnOutputSlotFails() {
        String yaml = """
                type: FURNACE
                deposit: [0, 1]
                output: [2]
                data: {0: 1}
                slots:
                  0: {material: AIR}
                  1: {material: AIR}
                  2:
                    ref: ingot
                """;
        assertThrows(MenuCompileException.class, () -> compile("furnace", yaml));
    }

    @Test
    void missingMaterialStillFailsWithoutRef() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    name: "Nope"
                """;
        MenuCompileException e = assertThrows(MenuCompileException.class, () -> compile("refs", yaml));
        assertTrue(e.getMessage().contains("ref"));
    }

    @Test
    void templateSlotsUnaffectedByRefRules() {
        String yaml = """
                rows: 1
                slots:
                  0:
                    material: STONE
                    name: "Plain %player%"
                """;
        CompiledForm form = compile("refs", yaml);
        assertNull(form.slots().get(0).itemRef());
        assertTrue(form.placeholderKeys().contains("player"));
    }
}
