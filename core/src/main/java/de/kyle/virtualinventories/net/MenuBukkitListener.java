package de.kyle.virtualinventories.net;

import de.kyle.virtualinventories.session.SessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up menu sessions when the viewer goes away. No close packet is sent
 * here, the client is either gone or resets its own screen on death.
 */
public final class MenuBukkitListener implements Listener {

    private final SessionManager sessions;

    public MenuBukkitListener(SessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.discard(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        sessions.discard(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        sessions.discard(event.getEntity());
    }
}
