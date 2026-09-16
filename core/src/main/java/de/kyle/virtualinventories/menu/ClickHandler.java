package de.kyle.virtualinventories.menu;

/**
 * Functional handler invoked when a player clicks a slot of a virtual menu.
 * Always runs on the Bukkit main thread. Never exposes real items, only snapshots.
 */
@FunctionalInterface
public interface ClickHandler {

    void handle(ClickContext context);

    static ClickHandler close() {
        return ctx -> ctx.session().close();
    }

    static ClickHandler message(String message) {
        return ctx -> ctx.player().sendMessage(message);
    }
}
