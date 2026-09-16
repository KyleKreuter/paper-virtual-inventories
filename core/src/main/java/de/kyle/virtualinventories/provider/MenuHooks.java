package de.kyle.virtualinventories.provider;

import de.kyle.virtualinventories.session.MenuSession;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

/**
 * Programmatic open/close hooks per menu (not serialized, registered in code).
 */
public record MenuHooks(BiConsumer<Player, MenuSession> onOpen, BiConsumer<Player, MenuSession> onClose) {

    public static MenuHooks empty() {
        return new MenuHooks(null, null);
    }
}
