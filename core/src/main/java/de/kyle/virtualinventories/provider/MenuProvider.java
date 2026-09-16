package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.serialize.MenuDefinition;
import de.kyle.virtualinventories.session.MenuSession;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

/**
 * Loads compiled menus and opens views for players.
 *
 * <p>The provider only ever loads compiled blobs (binary + gzip). Fresh
 * compilation from YAML happens purely to produce those blobs, even for the
 * in-memory path. Placeholders ({@code %key%}) are resolved per open through
 * {@link PlaceholderRegistry}, click actions ({@code action: some_id}) through
 * registered {@link ClickHandler}s.</p>
 *
 * <p>All methods must be called on the Bukkit main thread unless noted.</p>
 */
public interface MenuProvider {

    /** Compiles YAML now and loads the resulting blob (fresh-compile path). */
    void registerSource(String menuId, String yaml);

    /** Compiles a code-built definition now and loads the resulting blob. */
    void registerDefinition(MenuDefinition definition);

    /** Loads an already compiled blob (file/DB/network path). */
    void registerCompiled(String menuId, byte[] blob);

    /** Exports the compiled blob, e.g. to persist it as {@code .vmenu.gz}. */
    byte[] exportCompiled(String menuId);

    boolean has(String menuId);

    Set<String> menuIds();

    CompiledMenu compiled(String menuId);

    void unregister(String menuId);

    /** Recompiles all sources and re-reads the menus directory. */
    void reload();

    /** Compiles {@code menus/*.yml}, writes {@code compiled/*.vmenu.gz}, loads blobs. */
    void loadDirectory();

    PlaceholderRegistry placeholders();

    /** Binds a click-action id to a handler. */
    void action(String actionId, ClickHandler handler);

    void hooks(String menuId, MenuHooks hooks);

    MenuSession open(Player player, String menuId);

    MenuSession open(Player player, String menuId, Map<String, String> extra);

    /** Re-resolves dynamic slots of the player's open menu and resends them. */
    void refreshDynamic(Player player);

    void shutdown();
}
