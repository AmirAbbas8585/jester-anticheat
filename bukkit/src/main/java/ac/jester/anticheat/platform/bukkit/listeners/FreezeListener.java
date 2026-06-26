package ac.jester.anticheat.platform.bukkit.listeners;

import ac.jester.anticheat.GrimAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the freeze state on Bukkit. Frozen players are teleported back
 * to their frozen position when they try to move.
 */
public final class FreezeListener implements Listener {

    // UUID -> position where they were frozen
    private final Map<UUID, Location> frozenLocations = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!GrimAPI.INSTANCE.getFreezeManager().isFrozen(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Allow head rotation — only block positional movement
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Cache the freeze location on first movement attempt
        frozenLocations.computeIfAbsent(uuid, k -> from.clone());

        // Teleport them back silently
        event.setCancelled(true);
        player.teleport(frozenLocations.get(uuid));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Allow admin-initiated teleports (e.g. /skyac tp, /skyac setback)
        // but block self-initiated teleports (ender pearl, etc.) while frozen
        if (!GrimAPI.INSTANCE.getFreezeManager().isFrozen(uuid)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        frozenLocations.remove(uuid);
        // Auto-unfreeze on disconnect so they don't get stuck forever
        GrimAPI.INSTANCE.getFreezeManager().unfreeze(uuid);
    }
}
