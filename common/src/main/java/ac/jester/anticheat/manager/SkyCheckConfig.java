package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds per-check configuration loaded from config.yml.
 *
 * Each check has its own block under checks.<category>.<name>:
 *   enabled: true
 *   punishable: true
 *   max-violations: 10
 *   alert-interval: 1
 *   dont-alert-until: 1
 *   alert-cooldown-ms: 500
 *   minimum-tps: 15.0
 *   punishment-commands:
 *     - 'kick %player% &c[SkyAC] Unfair Advantage'
 *
 * Falls back to global defaults if a per-check value is absent.
 */
public final class SkyCheckConfig {

    private static final ConcurrentHashMap<String, CheckSettings> cache = new ConcurrentHashMap<>();

    private static volatile int defaultAlertInterval = 1;
    private static volatile int defaultDontAlertUntil = 1;
    private static volatile long defaultAlertCooldownMs = 500L;
    private static volatile double defaultMinimumTps = 15.0;
    private static volatile int defaultMaxViolations = 20;
    private static volatile boolean defaultPunishable = true;
    private static volatile long defaultPunishGraceMs = 2000L;
    private static volatile boolean experimentalChecksPunishable = false;

    public static void reloadGlobals(ConfigManager config) {
        defaultAlertInterval   = config.getIntElse("alert-defaults.alert-interval", 1);
        defaultDontAlertUntil  = config.getIntElse("alert-defaults.dont-alert-until", 1);
        defaultAlertCooldownMs = config.getIntElse("alert-defaults.alert-cooldown-ms", 500);
        defaultMinimumTps      = config.getDoubleElse("alert-defaults.minimum-tps", 15.0);
        defaultMaxViolations   = config.getIntElse("alert-defaults.max-violations", 20);
        defaultPunishable      = config.getBooleanElse("alert-defaults.punishable", true);
        defaultPunishGraceMs   = config.getIntElse("alert-defaults.punish-grace-ms", 2000);
        // The 39 checks marked experimental=true in code are explicitly NOT
        // considered reliable enough by Grim's own authors. Keeping
        // experimental-checks: true gives staff visibility (alerts/logs/the
        // GUI) into what they catch WITHOUT risking a false kick — unless this
        // is explicitly turned on, no experimental check can punish, no matter
        // what an individual checks.<Name>.punishable says.
        experimentalChecksPunishable = config.getBooleanElse("experimental-checks-punishable", false);
        // Grace window (ms) after a player joins, during which movement/timer
        // checks don't flag (mid-air spawn, chunk loading, ...).
        ac.jester.anticheat.player.GrimPlayer.joinGraceMs =
                config.getIntElse("movement-grace.join-ms", 3000);
        cache.clear();
    }

    public static boolean isExperimentalChecksPunishable() {
        return experimentalChecksPunishable;
    }

    /** Returns (and caches) per-check settings. configPath is e.g. "checks.movement.Simulation" */
    public static CheckSettings get(String checkName) {
        return cache.computeIfAbsent(checkName, SkyCheckConfig::load);
    }

    private static CheckSettings load(String checkName) {
        ConfigManager cfg = GrimAPI.INSTANCE.getConfigManager().getConfig();
        // config key: checks.<checkName> (e.g. checks.Simulation, checks.Reach)
        String prefix = "checks." + checkName + ".";

        boolean enabled       = cfg.getBooleanElse(prefix + "enabled", true);
        boolean punishable    = cfg.getBooleanElse(prefix + "punishable", defaultPunishable);
        int maxVL             = cfg.getIntElse(prefix + "max-violations", defaultMaxViolations);
        int alertInterval     = cfg.getIntElse(prefix + "alert-interval", defaultAlertInterval);
        int dontAlertUntil    = cfg.getIntElse(prefix + "dont-alert-until", defaultDontAlertUntil);
        long cooldownMs       = cfg.getIntElse(prefix + "alert-cooldown-ms", (int) defaultAlertCooldownMs);
        double minimumTps     = cfg.getDoubleElse(prefix + "minimum-tps", defaultMinimumTps);
        long punishGraceMs    = cfg.getIntElse(prefix + "punish-grace-ms", (int) defaultPunishGraceMs);
        List<String> commands = cfg.getStringListElse(prefix + "punishment-commands", List.of());

        return new CheckSettings(enabled, punishable, maxVL, alertInterval,
                dontAlertUntil, cooldownMs, minimumTps, punishGraceMs, commands);
    }

    public static final class CheckSettings {
        public final boolean enabled;
        public final boolean punishable;
        public final int maxViolations;
        public final int alertInterval;
        public final int dontAlertUntil;
        public final long alertCooldownMs;
        public final double minimumTps;
        public final long punishGraceMs;
        public final List<String> punishmentCommands;

        CheckSettings(boolean enabled, boolean punishable, int maxViolations,
                      int alertInterval, int dontAlertUntil, long alertCooldownMs,
                      double minimumTps, long punishGraceMs, List<String> punishmentCommands) {
            this.enabled = enabled;
            this.punishable = punishable;
            this.maxViolations = maxViolations;
            this.alertInterval = alertInterval;
            this.dontAlertUntil = dontAlertUntil;
            this.alertCooldownMs = alertCooldownMs;
            this.minimumTps = minimumTps;
            this.punishGraceMs = punishGraceMs;
            this.punishmentCommands = punishmentCommands;
        }
    }
}
