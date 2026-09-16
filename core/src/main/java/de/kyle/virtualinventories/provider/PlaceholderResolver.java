package de.kyle.virtualinventories.provider;

import org.bukkit.entity.Player;

/**
 * Resolves one placeholder key for the viewing player.
 * Called at most once per key per menu open (open scope), never per slot.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    String resolve(Player viewer);
}
