package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeluxeCombatHook implements PluginHook, Listener {
    private boolean available = false;
    private final ConcurrentHashMap<UUID, Long> pendingKnockback = new ConcurrentHashMap<>();
    private static final long KB_EXEMPT_WINDOW_MS = 500L;

    @Override
    public String getPluginName() { return "DeluxeCombat"; }

    @Override
    public void onEnable() {
        available = true;
        Plugin self = Bukkit.getPluginManager().getPlugin("JesterAntiCheat");
        if (self != null) {
            Bukkit.getPluginManager().registerEvents(this, self);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            markKnockbackApplied(player.getUniqueId());
        }
    }

    public void markKnockbackApplied(UUID player) {
        pendingKnockback.put(player, System.currentTimeMillis() + KB_EXEMPT_WINDOW_MS);
    }

    public boolean hasRecentCustomKB(UUID player) {
        Long expiry = pendingKnockback.get(player);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            pendingKnockback.remove(player);
            return false;
        }
        return true;
    }

    public void removePlayer(UUID player) {
        pendingKnockback.remove(player);
    }

    public boolean isAvailable() { return available; }
}
