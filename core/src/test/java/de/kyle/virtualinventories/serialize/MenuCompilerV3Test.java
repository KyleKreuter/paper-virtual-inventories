package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCompilerV3Test {

    private static CompiledForm compile(String id, String yaml) {
        return MenuCompiler.compile(DefinitionParser.parse(id, yaml), MenuCompiler.sha256Hex("test"));
    }

    private static final String FURNACE_YAML = """
            type: FURNACE
            title: "Furnace"
            slots:
              0:
                material: AIR
              1:
                material: AIR
              2:
                material: AIR
            deposit: [0, 1]
            output: [2]
            data:
              0: 200
              1: 200
              2: 0
              3: 200
            """;

    @Test
    void furnaceCompilesWithDataDepositAndOutput() {
        CompiledForm form = compile("furnace", FURNACE_YAML);
        assertEquals("FURNACE", form.windowType());
        assertEquals(Map.of(0, 200, 1, 200, 2, 0, 3, 200), form.containerData());
        assertEquals(List.of(2), form.outputSlots());
        assertTrue(form.trades().isEmpty());
    }

    @Test
    void depositNotAllowedForChest() {
        String yaml = """
                rows: 1
                title: "Chest"
                slots:
                  0:
                    material: AIR
                deposit: [0]
                """;
        assertThrows(MenuCompileException.class, () -> compile("chest", yaml));
    }

    @Test
    void outputOverlappingDepositFails() {
        String yaml = """
                type: FURNACE
                title: "Furnace"
                slots:
                  0:
                    material: AIR
                deposit: [0]
                output: [0]
                """;
        assertThrows(MenuCompileException.class, () -> compile("furnace", yaml));
    }

    @Test
    void tradesOnlyForMerchant() {
        String yaml = """
                type: FURNACE
                title: "Furnace"
                slots:
                  0:
                    material: AIR
                trades:
                  - buy_a: EMERALD
                    result: DIAMOND
                """;
        assertThrows(MenuCompileException.class, () -> compile("furnace", yaml));
    }

    @Test
    void dataKeyOutOfRangeFails() {
        String yaml = """
                type: FURNACE
                title: "Furnace"
                slots:
                  0:
                    material: AIR
                data:
                  300: 1
                """;
        assertThrows(MenuCompileException.class, () -> compile("furnace", yaml));
    }

    @Test
    void badButtonActionFails() {
        String yaml = """
                type: ENCHANTMENT
                title: "Enchant"
                slots:
                  0:
                    material: AIR
                  1:
                    material: AIR
                deposit: [0, 1]
                buttons:
                  0: "has space"
                """;
        assertThrows(MenuCompileException.class, () -> compile("enchant", yaml));
    }

    @Test
    void tradeWithoutResultFails() {
        String yaml = """
                type: MERCHANT
                title: "Trader"
                slots:
                  0:
                    material: AIR
                  1:
                    material: AIR
                  2:
                    material: AIR
                deposit: [0, 1]
                output: [2]
                trades:
                  - buy_a: EMERALD
                """;
        assertThrows(MenuCompileException.class, () -> compile("trader", yaml));
    }
}
