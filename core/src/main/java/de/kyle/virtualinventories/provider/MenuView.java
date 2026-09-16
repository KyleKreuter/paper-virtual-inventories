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
    private String text = "";

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

    /** Last rename text typed by the viewer (anvil only, max 50 chars enforced by session). */
    public String text() {
        return text;
    }

    public void text(String text) {
        this.text = text == null ? "" : text;
    }

    void updateScope(Map<String, String> newScope) {
        Map<String, String> merged = new LinkedHashMap<>(newScope);
        this.scope = Map.copyOf(merged);
    }
}
