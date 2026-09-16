package de.kyle.virtualinventories.demo;

import de.kyle.virtualinventories.serialize.CompiledForm;
import de.kyle.virtualinventories.serialize.DefinitionParser;
import de.kyle.virtualinventories.serialize.MenuCompiler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEngineTest {

    private static final String YAML = """
            type: MERCHANT
            rows: 1
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
                buy_a_count: 5
                result: DIAMOND_SWORD
                max_uses: 3
              - buy_a: COAL
                buy_a_count: 16
                buy_b: IRON_INGOT
                buy_b_count: 2
                result: DIAMOND
                max_uses: 12
            """;

    private static List<CompiledForm.CompiledTrade> trades() {
        CompiledForm form = MenuCompiler.compile(
                DefinitionParser.parse("trader", YAML), MenuCompiler.sha256Hex("test"));
        assertEquals(2, form.trades().size());
        return form.trades();
    }

    private static TradeEngine.SlotContent content(String material, int count) {
        return new TradeEngine.SlotContent(material, count);
    }

    @Test
    void matchesSingleCostTrade() {
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades(),
                content("EMERALD", 5), TradeEngine.SlotContent.empty(), trade -> 0);
        assertTrue(match.isPresent());
        assertEquals("DIAMOND_SWORD", match.get().trade().result());
        assertEquals(5, match.get().consumeA());
        assertEquals(0, match.get().consumeB());
    }

    @Test
    void rejectsSingleCostTradeWhenSecondSlotOccupied() {
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades(),
                content("EMERALD", 5), content("DIRT", 1), trade -> 0);
        assertTrue(match.isEmpty());
    }

    @Test
    void matchesTwoCostTrade() {
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades(),
                content("COAL", 20), content("IRON_INGOT", 2), trade -> 0);
        assertTrue(match.isPresent());
        assertEquals("DIAMOND", match.get().trade().result());
        assertEquals(16, match.get().consumeA());
        assertEquals(2, match.get().consumeB());
    }

    @Test
    void rejectsTradeWhenUsesExhausted() {
        Map<CompiledForm.CompiledTrade, Integer> uses = new HashMap<>();
        List<CompiledForm.CompiledTrade> trades = trades();
        uses.put(trades.get(0), 3);
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades,
                content("EMERALD", 64), TradeEngine.SlotContent.empty(),
                trade -> uses.getOrDefault(trade, 0));
        assertTrue(match.isEmpty());
    }

    @Test
    void matchesCaseInsensitive() {
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades(),
                content("emerald", 5), TradeEngine.SlotContent.empty(), trade -> 0);
        assertTrue(match.isPresent());
    }

    @Test
    void rejectsInsufficientCount() {
        Optional<TradeEngine.Match> match = TradeEngine.findMatch(trades(),
                content("EMERALD", 4), TradeEngine.SlotContent.empty(), trade -> 0);
        assertTrue(match.isEmpty());
    }
}
