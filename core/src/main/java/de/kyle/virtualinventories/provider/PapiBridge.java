package de.kyle.virtualinventories.provider;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional PlaceholderAPI fallback for keys without a registered resolver.
 * Purely reflective, so PlaceholderAPI is never a hard dependency.
 */
final class PapiBridge {

    private final boolean available;
    private final Method setPlaceholders;

    PapiBridge(Plugin plugin) {
        boolean ok = false;
        Method method = null;
        try {
            if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Class<?> offlinePlayer = Class.forName("org.bukkit.entity.OfflinePlayer");
                method = api.getMethod("setPlaceholders", offlinePlayer, String.class);
                ok = true;
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        this.available = ok;
        this.setPlaceholders = method;
    }

    boolean available() {
        return available;
    }

    String resolve(Player viewer, String key) {
        try {
            Object result = setPlaceholders.invoke(null, viewer, "%" + key + "%");
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
