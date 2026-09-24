package ac.jester.anticheat.manager;

import ac.jester.anticheat.checks.Check;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertRateLimiter {
    private final ConcurrentHashMap<String, PlayerCheckState> states = new ConcurrentHashMap<>();

    private static final class PlayerCheckState {
        volatile long lastAlertTime = 0L;
        volatile int suppressedCount = 0;
        final Object2IntOpenHashMap<String> violationCounts = new Object2IntOpenHashMap<>();
    }

    private String key(UUID uuid, String checkConfigName) {
        return uuid + ":" + checkConfigName;
    }

    public AlertDecision shouldAlert(UUID player, Check check, int currentVL,
                                     int alertInterval, int dontAlertUntil, long cooldownMs) {
        if (currentVL < dontAlertUntil) {
            return AlertDecision.suppress(0);
        }

        String key = key(player, check.getConfigName());
        PlayerCheckState state = states.computeIfAbsent(key, k -> new PlayerCheckState());

        if (alertInterval > 1 && (currentVL % alertInterval != 0)) {
            state.suppressedCount++;
            return AlertDecision.suppress(state.suppressedCount);
        }

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

    public void removePlayer(UUID player) {
        states.entrySet().removeIf(e -> e.getKey().startsWith(player + ":"));
    }

    public void resetCheck(UUID player, String checkConfigName) {
        states.remove(key(player, checkConfigName));
    }

    public static final class AlertDecision {
        public final boolean shouldSend;
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
