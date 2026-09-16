package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.menu.ClickHandler;
import de.kyle.virtualinventories.serialize.CompiledForm;
import de.kyle.virtualinventories.serialize.DefinitionParser;
import de.kyle.virtualinventories.serialize.MenuCodec;
import de.kyle.virtualinventories.serialize.MenuCompiler;
import de.kyle.virtualinventories.serialize.MenuCompileException;
import de.kyle.virtualinventories.serialize.MenuDefinition;
import de.kyle.virtualinventories.session.MenuSession;
import de.kyle.virtualinventories.session.SessionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link MenuProvider}: menus live compiled in memory.
 *
 * <p>Sources (YAML strings or code-built definitions) are only ever an input
 * to the compiler. The provider loads the resulting binary blob through the
 * same {@link #registerCompiled} path a file or database store would use.</p>
 */
public final class InMemoryMenuProvider implements MenuProvider {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final PlaceholderRegistry placeholders;
    private final Map<String, ClickHandler> actions = new ConcurrentHashMap<>();
    private final Map<String, MenuHooks> hooks = new ConcurrentHashMap<>();
    private final Map<String, String> yamlSources = new ConcurrentHashMap<>();
    private final Map<String, MenuDefinition> codeSources = new ConcurrentHashMap<>();
    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final Map<String, CompiledMenu> loaded = new ConcurrentHashMap<>();
    private volatile ItemProvider itemProvider = ItemProvider.empty();

    public InMemoryMenuProvider(Plugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.placeholders = new PlaceholderRegistry(plugin);
    }

    @Override
    public void registerSource(String menuId, String yaml) {
        yamlSources.put(menuId, yaml);
        MenuDefinition definition = DefinitionParser.parse(menuId, yaml);
        registerDefinition(definition, MenuCompiler.sha256Hex(yaml));
    }

    @Override
    public void registerDefinition(MenuDefinition definition) {
        codeSources.put(definition.id(), definition);
        registerDefinition(definition, MenuCompiler.sha256Hex(definition.toString()));
    }

    private void registerDefinition(MenuDefinition definition, String sourceShaHex) {
        CompiledForm form = MenuCompiler.compile(definition, sourceShaHex);
        registerCompiled(form.menuId(), MenuCodec.encode(form));
    }

    @Override
    public void registerCompiled(String menuId, byte[] blob) {
        CompiledForm form = MenuCodec.decode(blob);
        if (!form.menuId().equals(menuId)) {
            throw new MenuCompileException("[" + menuId + "] blob belongs to menu '" + form.menuId() + "'");
        }
        CompiledMenu menu = CompiledMenu.materialize(form, itemProvider);
        blobs.put(menuId, Arrays.copyOf(blob, blob.length));
        loaded.put(menuId, menu);
        plugin.getLogger().info("Loaded compiled menu '" + menuId + "' ("
                + form.slots().size() + " slots, " + form.placeholderKeys().size()
                + " placeholders, " + blob.length + " bytes)");
    }

    @Override
    public byte[] exportCompiled(String menuId) {
        byte[] blob = blobs.get(menuId);
        if (blob == null) {
            throw new IllegalArgumentException("Unknown menu '" + menuId + "'");
        }
        return Arrays.copyOf(blob, blob.length);
    }

    @Override
    public boolean has(String menuId) {
        return loaded.containsKey(menuId);
    }

    @Override
    public Set<String> menuIds() {
        return new TreeSet<>(loaded.keySet());
    }

    @Override
    public CompiledMenu compiled(String menuId) {
        CompiledMenu menu = loaded.get(menuId);
        if (menu == null) {
            throw new IllegalArgumentException("Unknown menu '" + menuId + "'");
        }
        return menu;
    }

    @Override
    public void unregister(String menuId) {
        yamlSources.remove(menuId);
        codeSources.remove(menuId);
        blobs.remove(menuId);
        loaded.remove(menuId);
        hooks.remove(menuId);
    }

    @Override
    public void reload() {
        loaded.clear();
        blobs.clear();
        new LinkedHashMap<>(yamlSources).forEach((id, yaml) -> {
            MenuDefinition definition = DefinitionParser.parse(id, yaml);
            registerDefinition(definition, MenuCompiler.sha256Hex(yaml));
        });
        new LinkedHashMap<>(codeSources).forEach((id, definition) ->
                registerDefinition(definition, MenuCompiler.sha256Hex(definition.toString())));
        loadDirectory();
    }

    @Override
    public void loadDirectory() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        File compiledDir = new File(plugin.getDataFolder(), "compiled");
        if (!menusDir.isDirectory() && !menusDir.mkdirs()) {
            throw new IllegalStateException("Cannot create menus directory " + menusDir);
        }
        if (!compiledDir.isDirectory() && !compiledDir.mkdirs()) {
            throw new IllegalStateException("Cannot create compiled directory " + compiledDir);
        }
        File[] files = menusDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String menuId = file.getName().substring(0, file.getName().length() - 4);
            String yaml;
            try {
                yaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new MenuCompileException("[" + menuId + "] cannot read " + file.getName(), e);
            }
            try {
                registerSource(menuId, yaml);
            } catch (MenuCompileException e) {
                throw new MenuCompileException("[" + menuId + "] " + file.getName() + ": " + e.getMessage(), e);
            }
            try {
                Files.write(new File(compiledDir, menuId + ".vmenu.gz").toPath(), exportCompiled(menuId));
            } catch (Exception e) {
                throw new MenuCompileException("[" + menuId + "] cannot write compiled blob", e);
            }
        }
    }

    @Override
    public PlaceholderRegistry placeholders() {
        return placeholders;
    }

    @Override
    public void setItemProvider(ItemProvider items) {
        this.itemProvider = items == null ? ItemProvider.empty() : items;
    }

    @Override
    public ItemProvider itemProvider() {
        return itemProvider;
    }

    @Override
    public void action(String actionId, ClickHandler handler) {
        if (actionId == null || !actionId.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid action id '" + actionId + "'");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler for '" + actionId + "' is null");
        }
        actions.put(actionId, handler);
    }

    @Override
    public void hooks(String menuId, MenuHooks menuHooks) {
        hooks.put(menuId, menuHooks == null ? MenuHooks.empty() : menuHooks);
    }

    @Override
    public MenuSession open(Player player, String menuId) {
        return open(player, menuId, Map.of());
    }

    @Override
    public MenuSession open(Player player, String menuId, Map<String, String> extra) {
        ensureMainThread();
        Target target = prepare(player, menuId, extra);
        return sessions.openSession(player, target.menu(), target.view(), target.handlers(), actions,
                target.hooks(), target.title(), target.content(), target.renderer());
    }

    @Override
    public MenuSession switchTo(Player player, String menuId) {
        return switchTo(player, menuId, Map.of());
    }

    @Override
    public MenuSession switchTo(Player player, String menuId, Map<String, String> extra) {
        ensureMainThread();
        Target target = prepare(player, menuId, extra);
        return sessions.switchSession(player, target.menu(), target.view(), target.handlers(), actions,
                target.hooks(), target.title(), target.content(), target.renderer());
    }

    /** Resolves everything needed to show a menu: no packets are sent here. */
    private Target prepare(Player player, String menuId, Map<String, String> extra) {
        CompiledMenu menu = compiled(menuId);
        Map<String, String> safeExtra = extra == null ? Map.of() : extra;
        assertResolvable(menu, safeExtra);
        Map<Integer, ClickHandler> handlers = resolveHandlers(menu);
        Map<String, String> scope = placeholders.resolve(player, menu.placeholderKeys(), safeExtra);
        MenuView view = new MenuView(menu, safeExtra, scope);
        Component title = menu.renderTitle(scope);
        ItemStack[] content = renderFull(menu, view);
        MenuHooks menuHooks = hooks.getOrDefault(menuId, MenuHooks.empty());
        return new Target(menu, view, handlers, menuHooks, title, content, () -> renderFull(menu, view));
    }

    private record Target(CompiledMenu menu, MenuView view, Map<Integer, ClickHandler> handlers,
                          MenuHooks hooks, Component title, ItemStack[] content,
                          MenuSession.ContentRenderer renderer) {
    }

    @Override
    public void refreshDynamic(Player player) {
        ensureMainThread();
        MenuSession session = sessions.get(player);
        if (session == null || !session.isOpen()) {
            return;
        }
        MenuView view = session.view();
        Map<String, String> scope = placeholders.resolve(player, view.menu().placeholderKeys(), view.extra());
        view.updateScope(scope);
        session.refresh();
    }

    @Override
    public void shutdown() {
        sessions.closeAll();
    }

    private ItemStack[] renderFull(CompiledMenu menu, MenuView view) {
        ItemStack[] content = menu.renderStaticView();
        for (CompiledMenu.DynamicSlot slot : menu.dynamicSlots()) {
            content[slot.slot()] = menu.renderDynamic(slot, view.scope());
        }
        return content;
    }

    private void assertResolvable(CompiledMenu menu, Map<String, String> extra) {
        List<String> missing = new ArrayList<>();
        for (String key : menu.placeholderKeys()) {
            if (!extra.containsKey(key) && !placeholders.has(key) && !placeholders.papiAvailable()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new MenuCompileException("[" + menu.id() + "] unresolvable placeholders " + missing
                    + " (no resolver, no extra value, PlaceholderAPI missing)");
        }
    }

    private Map<Integer, ClickHandler> resolveHandlers(CompiledMenu menu) {
        Map<Integer, ClickHandler> handlers = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : actionEntries(menu)) {
            ClickHandler handler = actions.get(entry.getValue());
            if (handler == null) {
                throw new MenuCompileException("[" + menu.id() + "] unknown action '"
                        + entry.getValue() + "' (no handler registered)");
            }
            handlers.put(entry.getKey(), handler);
        }
        for (Map.Entry<Integer, String> entry : menu.buttonActions().entrySet()) {
            if (!actions.containsKey(entry.getValue())) {
                throw new MenuCompileException("[" + menu.id() + "] unknown button action '"
                        + entry.getValue() + "' on button " + entry.getKey()
                        + " (no handler registered)");
            }
        }
        return handlers;
    }

    private List<Map.Entry<Integer, String>> actionEntries(CompiledMenu menu) {
        List<Map.Entry<Integer, String>> entries = new ArrayList<>();
        for (int slot = 0; slot < menu.slotCount(); slot++) {
            String action = menu.action(slot);
            if (action != null) {
                entries.add(Map.entry(slot, action));
            }
        }
        return entries;
    }

    private void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MenuProvider must be used on the main thread.");
        }
    }
}
