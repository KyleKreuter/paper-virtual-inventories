package de.kyle.virtualinventories.provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-open state of one viewer: the compiled menu plus the resolved
 * placeholder scope and the per-open extra values (kept for refreshes).
 */
public final class MenuView {

    private final CompiledMenu menu;
    private final Map<String, String> extra;
    private Map<String, String> scope;

    MenuView(CompiledMenu menu, Map<String, String> extra, Map<String, String> scope) {
        this.menu = menu;
        this.extra = Map.copyOf(extra);
        this.scope = Map.copyOf(scope);
    }

    public CompiledMenu menu() {
        return menu;
    }

    public String menuId() {
        return menu.id();
    }

    public Map<String, String> scope() {
        return scope;
    }

    public Map<String, String> extra() {
        return extra;
    }

    void updateScope(Map<String, String> newScope) {
        Map<String, String> merged = new LinkedHashMap<>(newScope);
        this.scope = Map.copyOf(merged);
    }
}
