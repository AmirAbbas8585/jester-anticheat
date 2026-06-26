package ac.jester.anticheat.manager;

import ac.jester.anticheat.checks.Check;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-check alert rate limiter.
 * Prevents alert spam when a cheat triggers many violations in quick succession.
 *
 * Three controls (all configurable per check via config.yml):
 *   alert-interval     — only send alert every N violations (e.g. 2 = VL 2, 4, 6 ...)
 *   dont-alert-until   — suppress alerts whose VL is below this threshold
 *   alert-cooldown-ms  — minimum milliseconds between consecutive alerts for the same check/player
 */
public final class AlertRateLimiter {

    // Per-player state keyed by "UUID:checkConfigName"
    private final ConcurrentHashMap<String, PlayerCheckState> states = new ConcurrentHashMap<>();

    private static final class PlayerCheckState {
        volatile long lastAlertTime = 0L;
        volatile int suppressedCount = 0;
        final Object2IntOpenHashMap<String> violationCounts = new Object2IntOpenHashMap<>();
    }

    private String key(UUID uuid, String checkConfigName) {
        return uuid + ":" + checkConfigName;
    }

    /**
     * Decides whether an alert should be sent.
     *
     * @param player          Bukkit UUID of the player
     * @param check           The check that flagged
     * @param currentVL       Current total violation count for this check
     * @param alertInterval   Send every Nth violation (1 = every violation)
     * @param dontAlertUntil  Don't send until VL >= this value
     * @param cooldownMs      Minimum ms between alerts for this check (0 = no cooldown)
     * @return AlertDecision — SEND, SUPPRESS, or SEND_WITH_SUPPRESSED
     */
    public AlertDecision shouldAlert(UUID player, Check check, int currentVL,
                                     int alertInterval, int dontAlertUntil, long cooldownMs) {
        if (currentVL < dontAlertUntil) {
            return AlertDecision.suppress(0);
        }

        String key = key(player, check.getConfigName());
        PlayerCheckState state = states.computeIfAbsent(key, k -> new PlayerCheckState());

        // Violation-interval check: only alert at multiples of alertInterval
        if (alertInterval > 1 && (currentVL % alertInterval != 0)) {
            state.suppressedCount++;
            return AlertDecision.suppress(state.suppressedCount);
        }

        // Cooldown check
        if (cooldownMs > 0) {
            long now = System.currentTimeMillis();
            if (now - state.lastAlertTime < cooldownMs) {
                state.suppressedCount++;
                return AlertDecision.suppress(state.suppressedCount);
            }
            state.lastAlertTime = now;
        }

        int suppressed = state.suppressedCount;
        state.suppressedCount = 0;
        return AlertDecision.send(suppressed);
    }

    /** Remove all state for a player when they disconnect */
    public void removePlayer(UUID player) {
        states.entrySet().removeIf(e -> e.getKey().startsWith(player + ":"));
    }

    /** Reset state for a specific check on a player */
    public void resetCheck(UUID player, String checkConfigName) {
        states.remove(key(player, checkConfigName));
    }

    /** Result of a rate-limit decision */
    public static final class AlertDecision {
        public final boolean shouldSend;
        /** How many alerts were suppressed before this one (0 if none) */
        public final int suppressedBefore;

        private AlertDecision(boolean shouldSend, int suppressedBefore) {
            this.shouldSend = shouldSend;
            this.suppressedBefore = suppressedBefore;
        }

        static AlertDecision send(int suppressed) {
            return new AlertDecision(true, suppressed);
        }

        static AlertDecision suppress(int suppressed) {
            return new AlertDecision(false, suppressed);
        }
    }
}
