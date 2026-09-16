package de.kyle.virtualinventories.provider;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of placeholder resolvers plus resolution scopes.
 *
 * <p>Resolution order per key: per-open {@code extra} values first, then
 * registered resolvers, then the PlaceholderAPI bridge (if installed).
 * Unknown keys resolve to an empty string.</p>
 */
public final class PlaceholderRegistry {

    private final Plugin plugin;
    private final Map<String, PlaceholderResolver> resolvers = new ConcurrentHashMap<>();
    private final PapiBridge papiBridge;

    public PlaceholderRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.papiBridge = new PapiBridge(plugin);
        register("player", Player::getName);
    }

    public void register(String key, PlaceholderResolver resolver) {
        if (key == null || !key.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid placeholder key '" + key + "'");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver for '" + key + "' is null");
        }
        resolvers.put(key, resolver);
    }

    public boolean has(String key) {
        return resolvers.containsKey(key);
    }

    public boolean papiAvailable() {
        return papiBridge.available();
    }

    /**
     * Resolves exactly the requested keys, each at most once.
     * Must be called on the main thread (resolvers may touch game state).
     */
    public Map<String, String> resolve(Player viewer, Set<String> keys, Map<String, String> extra) {
        Map<String, String> scope = new LinkedHashMap<>();
        for (String key : keys) {
            if (extra != null && extra.containsKey(key)) {
                scope.put(key, orEmpty(extra.get(key)));
                continue;
            }
            PlaceholderResolver resolver = resolvers.get(key);
            if (resolver != null) {
                try {
                    scope.put(key, orEmpty(resolver.resolve(viewer)));
                } catch (Exception e) {
                    plugin.getLogger().warning("Placeholder resolver '" + key + "' failed: " + e.getMessage());
                    scope.put(key, "");
                }
                continue;
            }
            if (papiBridge.available()) {
                scope.put(key, papiBridge.resolve(viewer, key));
                continue;
            }
            scope.put(key, "");
        }
        return scope;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
