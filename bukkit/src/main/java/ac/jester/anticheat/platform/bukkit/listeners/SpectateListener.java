package ac.jester.anticheat.platform.bukkit.listeners;

import ac.jester.anticheat.GrimAPI;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

/**
 * Keeps a staff member in spectator for as long as they are spectating.
 *
 * Setting the gamemode around the teleport is not enough on its own. A
 * cross-world teleport makes the server re-apply the destination world's
 * gamemode, and anything doing per-world gamemodes — Multiverse's
 * enforce-gamemode, lobby and world-manager plugins — puts the player back into
 * survival on arrival. Trying to win that by re-asserting the gamemode at the
 * right moment is a race: the other plugin may act on the world-change event,
 * on a delayed task, or a tick later, and there is no ordering we can rely on.
 *
 * So this does not race. It refuses the change outright: while a player is
 * spectating through /jester spectate, nothing may move them out of spectator.
 * Whenever the override happens and whoever causes it, the answer is the same.
 *
 * Leaving spectate goes through SpectateManager.disable(), which removes the
 * player from the spectating set before restoring their old gamemode, so the
 * restore is not blocked by this.
 */
public final class SpectateListener implements Listener {

    /**
     * HIGHEST rather than MONITOR so this still runs before the final decision,
     * and ignoreCancelled is off because a cancelled change is already fine.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) return; // that's us
        Player player = event.getPlayer();
        if (!GrimAPI.INSTANCE.getSpectateManager().isSpectating(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * Belt and braces: if a plugin changes the gamemode without firing the event
     * (or the server applies the world default internally), put it back.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!GrimAPI.INSTANCE.getSpectateManager().isSpectating(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        player.setGameMode(GameMode.SPECTATOR);
    }
}
