package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionParserTest {

    private static final String YAML = """
            rows: 3
            title: "<gold>Shop"
            slots:
              10:
                material: DIAMOND
                amount: 2
                name: "<aqua>Diamond <gray>(%balance%)"
                lore:
                  - "<gray>Price: <green>%price%"
                  - "static line"
                flags: [HIDE_ATTRIBUTES]
                custom_model_data: 5
                action: buy_diamond
              11:
                material: STONE
                amount: "%kills%"
            """;

    @Test
    void parsesFullSlot() {
        MenuDefinition def = DefinitionParser.parse("shop", YAML);
        assertEquals("shop", def.id());
        assertEquals(3, def.rows());
        assertEquals("<gold>Shop", def.title());
        assertEquals(2, def.slots().size());

        MenuDefinition.SlotDefinition slot10 = def.slots().get(10);
        assertEquals("DIAMOND", slot10.item().material());
        assertEquals(2, slot10.item().amount());
        assertNull(slot10.item().amountPlaceholder());
        assertEquals("<aqua>Diamond <gray>(%balance%)", slot10.item().name());
        assertEquals(2, slot10.item().lore().size());
        assertEquals("buy_diamond", slot10.action());
        assertTrue(slot10.item().flags().contains("HIDE_ATTRIBUTES"));
        assertEquals(5, slot10.item().customModelData());
    }

    @Test
    void parsesAmountPlaceholder() {
        MenuDefinition def = DefinitionParser.parse("shop", YAML);
        MenuDefinition.SlotDefinition slot11 = def.slots().get(11);
        assertEquals("STONE", slot11.item().material());
        assertEquals("kills", slot11.item().amountPlaceholder());
        assertNull(slot11.item().name());
        assertNull(slot11.action());
    }

    @Test
    void missingMaterialFails() {
        String yaml = "rows: 1\nslots:\n  0:\n    name: x\n";
        assertThrows(MenuCompileException.class, () -> DefinitionParser.parse("m", yaml));
    }

    @Test
    void nonNumericSlotKeyFails() {
        String yaml = "rows: 1\nslots:\n  top:\n    material: STONE\n";
        assertThrows(MenuCompileException.class, () -> DefinitionParser.parse("m", yaml));
    }

    @Test
    void idMismatchFails() {
        String yaml = "id: other\nrows: 1\n";
        assertThrows(MenuCompileException.class, () -> DefinitionParser.parse("m", yaml));
    }

    @Test
    void brokenYamlFails() {
        assertThrows(MenuCompileException.class, () -> DefinitionParser.parse("m", "rows: [unclosed"));
    }

    @Test
    void badAmountStringFails() {
        String yaml = "rows: 1\nslots:\n  0:\n    material: STONE\n    amount: \"many %kills%\"\n";
        assertThrows(MenuCompileException.class, () -> DefinitionParser.parse("m", yaml));
    }
}
