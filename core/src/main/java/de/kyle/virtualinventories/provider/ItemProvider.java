package de.kyle.virtualinventories.provider;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Named, fully serialized item stacks referenced by {@code ref:} slots.
 *
 * <p>The provider only stores references (ids) in compiled menus; stacks are
 * resolved once at menu load. {@link #get(String)} must return a <b>fresh
 * copy</b> on every call so the library can hand out mutable stacks without
 * aliasing the stored template.</p>
 *
 * <p>All methods must be called on the Bukkit main thread unless noted.</p>
 */
public interface ItemProvider {

    /**
     * Resolves an id to a fresh, mutable stack.
     *
     * @throws java.util.NoSuchElementException if the id is unknown
     */
    ItemStack get(String id);

    boolean has(String id);

    Set<String> ids();

    /** Re-reads the backing store (file, database, ...). */
    void reload();

    /** Provider without items: every {@code ref:} fails fast at menu load. */
    static ItemProvider empty() {
        return new ItemProvider() {
            @Override
            public ItemStack get(String id) {
                throw new NoSuchElementException("Unknown item ref '" + id
                        + "' (no ItemProvider registered — call menus.setItemProvider(...))");
            }

            @Override
            public boolean has(String id) {
                return false;
            }

            @Override
            public Set<String> ids() {
                return Set.of();
            }

            @Override
            public void reload() {
            }
        };
    }

    /** Validates an item id ({@code [A-Za-z0-9_.-]+}). */
    static String requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                    "Invalid item id '" + id + "' (expected [A-Za-z0-9_.-]+)");
        }
        return id;
    }

    /** Backing snapshot helper for map-based providers. */
    static Map<String, String> copyOfBlobs(Map<String, String> blobs) {
        return Map.copyOf(blobs);
    }
}
