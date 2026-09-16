package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.serialize.ItemBlobs;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@link ItemProvider} backed by a flat {@code items.yml} in the plugin's
 * data folder ({@code id: base64-blob} per line). Ships with the library so
 * {@code ref:} works without any database.
 *
 * <pre>{@code
 * crown: "H4sIAAAAAAAA/2WQ..."
 * starter_kit: "H4sIAAAAAAAA/22Q..."
 * }</pre>
 *
 * <p>Decoding happens on {@link #get(String)}, so edits to the file take
 * effect after {@link #reload()} without recompiling any menu. Blobs are
 * server-version-bound (see {@link ItemBlobs}); re-export after updates.</p>
 */
public final class FileItemProvider implements ItemProvider {

    private final Plugin plugin;
    private final String fileName;
    private final Map<String, String> blobs = new LinkedHashMap<>();

    private FileItemProvider(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    /** Loads {@code items.yml} from the plugin's data folder now. */
    public static FileItemProvider fromDataFolder(Plugin plugin) {
        return fromDataFolder(plugin, "items.yml");
    }

    /** Loads {@code fileName} from the plugin's data folder now. */
    public static FileItemProvider fromDataFolder(Plugin plugin, String fileName) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(fileName, "fileName");
        FileItemProvider provider = new FileItemProvider(plugin, fileName);
        provider.reload();
        return provider;
    }

    /**
     * Stores (or overwrites) an id from a live stack and persists the file.
     * This is what {@code /vitem export} calls.
     */
    public synchronized void exportItem(String id, ItemStack stack) {
        ItemProvider.requireId(id);
        Objects.requireNonNull(stack, "stack");
        blobs.put(id, ItemBlobs.encode(stack));
        save();
    }

    @Override
    public synchronized ItemStack get(String id) {
        String blob = blobs.get(id);
        if (blob == null) {
            throw new java.util.NoSuchElementException("Unknown item ref '" + id + "'");
        }
        return ItemBlobs.decode(blob);
    }

    @Override
    public synchronized boolean has(String id) {
        return blobs.containsKey(id);
    }

    @Override
    public synchronized Set<String> ids() {
        return new TreeSet<>(blobs.keySet());
    }

    @Override
    public synchronized void reload() {
        blobs.clear();
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            if (!cfg.isString(key)) {
                throw new IllegalStateException("[" + fileName + "] item '" + key + "' is not a string");
            }
            blobs.put(key, cfg.getString(key));
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, String> entry : blobs.entrySet()) {
            cfg.set(entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), fileName));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write " + fileName, e);
        }
    }
}
