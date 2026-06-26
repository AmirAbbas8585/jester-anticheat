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

/**
 * Hooks into DeluxeCombat to track when custom knockback is applied.
 * Velocity checks must be exempted for the tick(s) following a plugin-applied knockback
 * to avoid false Velocity/AntiKB flags.
 *
 * markKnockbackApplied/hasRecentCustomKB previously existed but were never
 * actually wired to anything — no listener ever called the former, and no
 * check ever consulted the latter, so this "tolerance" never did anything
 * despite the config explicitly claiming it was active. Real logs showed
 * MovementA false-flagging with large (~0.14-0.33), isolated, one-off offset
 * spikes — consistent with a non-vanilla knockback velocity the general
 * movement predictor (unlike AntiKB, which reads the actual sent packet
 * directly) doesn't fully account for. Listening for EntityDamageEvent on the
 * player marks the window regardless of which plugin computed the knockback
 * (vanilla or DeluxeCombat) — broader than strictly necessary, but combat
 * knockback timing is hard to predict exactly for a tick or two either way.
 */
public final class DeluxeCombatHook implements PluginHook, Listener {

    private boolean available = false;
    // UUID -> expiry time (ms). During this window, velocity checks are softened.
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

    /**
     * Called by our own combat detection or DeluxeCombat API when custom KB is applied.
     * Marks the player as having recently received non-standard knockback.
     */
    public void markKnockbackApplied(UUID player) {
        pendingKnockback.put(player, System.currentTimeMillis() + KB_EXEMPT_WINDOW_MS);
    }

    /**
     * Returns true if this player recently received plugin-applied knockback.
     * Velocity checks should apply more tolerance in this window.
     */
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
