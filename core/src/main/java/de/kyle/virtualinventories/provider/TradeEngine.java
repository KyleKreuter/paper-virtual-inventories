package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.serialize.CompiledForm;

import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Pure merchant trade matching without any Bukkit types, so trade logic
 * is unit-testable without a server.
 *
 * <p>Matching is intentionally strict: the first deposit slot must satisfy
 * the first cost, the second slot the optional second cost. A trade without
 * a second cost requires an empty second slot. Uses, XP, special prices and
 * demand are ignored (uses are checked against maxUses only).</p>
 */
public final class TradeEngine {

    private TradeEngine() {
    }

    /** Content snapshot of one merchant input slot. */
    public record SlotContent(String materialKey, int count) {

        public static SlotContent empty() {
            return new SlotContent(null, 0);
        }

        public boolean isEmpty() {
            return materialKey == null || count <= 0;
        }
    }

    /** A matched trade plus how much to consume from each input slot. */
    public record Match(CompiledForm.CompiledTrade trade, int consumeA, int consumeB) {
    }

    /**
     * Finds the first trade whose costs are satisfied and which still has
     * uses left.
     */
    public static Optional<Match> findMatch(List<CompiledForm.CompiledTrade> trades,
                                            SlotContent first, SlotContent second,
                                            ToIntFunction<CompiledForm.CompiledTrade> uses) {
        for (CompiledForm.CompiledTrade trade : trades) {
            if (uses.applyAsInt(trade) >= trade.maxUses()) {
                continue;
            }
            if (!matches(first, trade.buyA(), trade.buyACount())) {
                continue;
            }
            if (trade.buyB() == null) {
                if (!second.isEmpty()) {
                    continue;
                }
            } else if (!matches(second, trade.buyB(), trade.buyBCount())) {
                continue;
            }
            return Optional.of(new Match(trade,
                    trade.buyACount(), trade.buyB() == null ? 0 : trade.buyBCount()));
        }
        return Optional.empty();
    }

    private static boolean matches(SlotContent slot, String material, int count) {
        return !slot.isEmpty() && slot.materialKey().equalsIgnoreCase(material) && slot.count() >= count;
    }
}
