package ac.jester.anticheat.platform.bukkit.listeners;

import ac.jester.anticheat.GrimAPI;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public final class SpectateListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) return;
        Player player = event.getPlayer();
        if (!GrimAPI.INSTANCE.getSpectateManager().isSpectating(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!GrimAPI.INSTANCE.getSpectateManager().isSpectating(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        player.setGameMode(GameMode.SPECTATOR);
    }
}
