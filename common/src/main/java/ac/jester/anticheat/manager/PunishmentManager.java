package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.config.ConfigReloadable;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.events.packets.ProxyAlertMessenger;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PunishmentManager implements ConfigReloadable {
    private final GrimPlayer player;
    private final List<PunishGroup> groups = new ArrayList<>();
    private String experimentalSymbol = "*";
    private String alertString;
    private boolean testMode;
    private String proxyAlertString = "";
    private boolean clickableAlerts = true;
    // Config names of checks whose punishment has already fired this offense.
    // Prevents re-kicking, and (unlike the old "vl == max" test) fires reliably
    // even when the violation count jumps past the exact threshold.
    private final java.util.Set<String> punishedChecks = new java.util.HashSet<>();
    // When the current violation streak for a check started (first flag), used to
    // enforce punish-grace-ms — a kick can't fire until the streak has lasted at
    // least that long, so a sub-second burst that instantly reaches max-violations
    // no longer kicks "out of nowhere".
    private final java.util.Map<String, Long> offenseFirstFlagMs = new java.util.HashMap<>();
    // Highest transaction ping seen across the flags of the current streak. The
    // kick message's %ping% uses THIS (the ping while the player was actually
    // racking up the flags that caused the kick) rather than the instantaneous
    // ping at the exact kick tick, which is often already back to normal.
    private final java.util.Map<String, Integer> offensePeakPing = new java.util.HashMap<>();

    public PunishmentManager(GrimPlayer player) {
        this.player = player;
    }

    @Override
    public void reload(ConfigManager config) {
        List<String> punish = config.getStringListElse("Punishments", new ArrayList<>());
        experimentalSymbol = config.getStringElse("experimental-symbol", "*");
        alertString = config.getStringElse("alerts-format", "%prefix% &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");
        testMode = config.getBooleanElse("test-mode", false);
        proxyAlertString = config.getStringElse("alerts-format-proxy", "%prefix% &f[&cproxy&f] &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");
        clickableAlerts = config.getBooleanElse("alerts.clickable", true);
        try {
            groups.clear();

            // To support reloading
            for (AbstractCheck check : player.checkManager.allChecks.values()) {
                check.setEnabled(false);
            }

            for (Object s : punish) {
                LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) s;

                List<String> checks = (List<String>) map.getOrDefault("checks", new ArrayList<>());
                List<String> commands = (List<String>) map.getOrDefault("commands", new ArrayList<>());
                int removeViolationsAfter = (int) map.getOrDefault("remove-violations-after", 300);

                List<ParsedCommand> parsed = new ArrayList<>();
                List<AbstractCheck> checksList = new ArrayList<>();
                List<AbstractCheck> excluded = new ArrayList<>();
                for (String command : checks) {
                    command = command.toLowerCase(Locale.ROOT);
                    boolean exclude = false;
                    if (command.startsWith("!")) {
                        exclude = true;
                        command = command.substring(1);
                    }
                    for (AbstractCheck check : player.checkManager.allChecks.values()) { // o(n) * o(n)?
                        if (check.getCheckName() != null &&
                                (check.getCheckName().toLowerCase(Locale.ROOT).contains(command)
                                        || check.getAlternativeName().toLowerCase(Locale.ROOT).contains(command))) { // Some checks have equivalent names like AntiKB and AntiKnockback
                            if (exclude) {
                                excluded.add(check);
                            } else {
                                checksList.add(check);
                                check.setEnabled(true);
                            }
                        }
                    }
                    for (AbstractCheck check : excluded) checksList.remove(check);
                }

                for (String command : commands) {
                    String firstNum = command.substring(0, command.indexOf(":"));
                    String secondNum = command.substring(command.indexOf(":"), command.indexOf(" "));

                    int threshold = Integer.parseInt(firstNum);
                    int interval = Integer.parseInt(secondNum.substring(1));
                    String commandString = command.substring(command.indexOf(" ") + 1);

                    parsed.add(new ParsedCommand(threshold, interval, commandString));
                }

                groups.add(new PunishGroup(checksList, parsed, removeViolationsAfter * 1000));
            }
        } catch (Exception e) {
            LogUtil.error("Error while loading punishments.yml! This is likely your fault!", e);
        }
    }

    private String replaceAlertPlaceholders(String original, int vl, Check check, String verbose) {
        // %player% is the plain name — the alert format colors it, and the
        // hover/click is applied to the WHOLE alert component in handleAlert
        return MessageUtil.replacePlaceholders(player, original
                .replace("[alert]", alertString)
                .replace("[proxy]", proxyAlertString)
                .replace("%player%", player.user.getName())
                .replace("%check_name%", check.getDisplayName())
                .replace("%experimental%", "") // experimental ✦ marker removed
                .replace("%vl%", Integer.toString(vl))
                .replace("%description%", check.getDescription())
        ).replace("%verbose%", MiniMessage.miniMessage().escapeTags(verbose));
    }

    /**
     * Builds the rich hover tooltip shown over the entire alert line.
     * Unified palette: #c19a6b (accent/numbers), #ff5348 (check/danger),
     * #a9c8ff (player/values), white (labels).
     */
    private String buildAlertHover(int vl, int maxVl, Check check, String verbose) {
        MiniMessage mm = MiniMessage.miniMessage();
        String brand = mm.escapeTags((player.getBrand() != null ? player.getBrand() : "unknown").replace("'", ""));
        String safeVerbose = mm.escapeTags(verbose == null ? "" : verbose).replace("'", "");
        // Info: used to just be the check's static description ("Breaking blocks
        // too quickly") — same text every time, no help telling a false flag
        // from a real one. Now it parses the actual verbose data (ping, offset,
        // block type, sneak/water/...) and writes a per-flag explanation in
        // Finglish, so staff can judge at a glance without pasting the row
        // somewhere and asking.
        String description = mm.escapeTags(ac.jester.anticheat.utils.anticheat.FlagExplainer
                .explain(check.getConfigName(), verbose, vl, player.getTransactionPing(),
                        GrimAPI.INSTANCE.getPlatformServer().getTPS())
                .replace("'", ""));
        var clientVersion = player.getClientVersion();
        String version = clientVersion != null ? clientVersion.getReleaseName() : "?";
        return String.format(
                "<color:#c19a6b>•</color> <white>Player:</white> <color:#a9c8ff>%s</color><newline>" +
                "<color:#c19a6b>•</color> <white>Brand:</white> <color:#a9c8ff>%s</color> <color:#c19a6b>(%s)</color><newline>" +
                "<color:#c19a6b>•</color> <white>Check:</white> <color:#ff5348>%s</color><newline>" +
                "<color:#c19a6b>•</color> <white>Violations:</white> <color:#ff5348>x%d</color> <white>/</white> <color:#c19a6b>%d</color><newline>" +
                "<color:#c19a6b>•</color> <white>Ping:</white> <color:#c19a6b>%dms</color>  <white>TPS:</white> <color:#c19a6b>%.1f</color><newline>" +
                "<color:#c19a6b>•</color> <white>Position:</white> <color:#a9c8ff>%.0f, %.0f, %.0f</color> <dark_gray>(</dark_gray><color:#a9c8ff>%s</color><dark_gray>)</dark_gray><newline>" +
                "<newline><white>Info:</white> <color:#a9c8ff>%s</color><newline>" +
                "<white>Verbose:</white> <color:#a9c8ff>%s</color><newline>" +
                "<newline><color:#c19a6b>•</color> <white>Click to teleport</white>",
                player.user.getName(),
                brand,
                version,
                check.getDisplayName(),
                vl, maxVl,
                player.getTransactionPing(),
                GrimAPI.INSTANCE.getPlatformServer().getTPS(),
                player.x, player.y, player.z,
                player.gamemode != null ? player.gamemode.name() : "UNKNOWN",
                description,
                safeVerbose
        );
    }

    public boolean handleAlert(GrimPlayer player, String verbose, Check check) {
        boolean sentDebug = false;

        // ── JesterAC: per-check alert rate limiting ──────────────────────────────
        JesterCheckConfig.CheckSettings checkCfg = JesterCheckConfig.get(check.getConfigName());
        if (!checkCfg.enabled) return false;

        int currentVL = (int) check.violations;
        AlertRateLimiter.AlertDecision decision = GrimAPI.INSTANCE.getAlertRateLimiter().shouldAlert(
                player.uuid,
                check,
                currentVL,
                checkCfg.alertInterval,
                checkCfg.dontAlertUntil,
                checkCfg.alertCooldownMs
        );

        // ── JesterAC: per-check punishment execution ─────────────────────────────
        // Experimental checks (39 of them — marked unreliable by Grim's own
        // authors) can still alert/log for staff visibility, but never punish
        // unless experimental-checks-punishable is explicitly turned on.
        boolean allowedToPunish = checkCfg.punishable
                && (!check.isExperimental() || JesterCheckConfig.isExperimentalChecksPunishable());
        if (allowedToPunish && !checkCfg.punishmentCommands.isEmpty()) {
            final String punishKey = check.getConfigName();
            final long now = System.currentTimeMillis();
            if (currentVL > 0) {
                // Track the streak: remember when it started and the worst ping
                // seen during it. putIfAbsent keeps the first-flag timestamp.
                offenseFirstFlagMs.putIfAbsent(punishKey, now);
                offensePeakPing.merge(punishKey, player.getTransactionPing(), Math::max);
            }
            if (currentVL >= checkCfg.maxViolations) {
                // punish-grace-ms: don't kick until the streak has lasted long
                // enough. A real cheat keeps flagging well past this; a one-off
                // burst that spiked straight to max-violations decays away first.
                long streakStart = offenseFirstFlagMs.getOrDefault(punishKey, now);
                boolean graceElapsed = now - streakStart >= checkCfg.punishGraceMs;
                // Ping shown in the kick message: the peak ping recorded across
                // the flags that caused the kick, not the (often already-
                // recovered) instantaneous ping at this exact tick.
                int punishPing = offensePeakPing.getOrDefault(punishKey, player.getTransactionPing());
                // High-ping guard: laggy players desync and false-flag movement/
                // combat checks, so above this ping we still alert but DON'T kick.
                // Packet-integrity checks (BadPackets/Crash/Exploit) are ping-
                // independent and must still enforce, so they bypass the guard.
                int noPunishAbove = GrimAPI.INSTANCE.getConfigManager().getConfig()
                        .getIntElse("high-ping.no-punish-above-ms", 400);
                String cn = check.getCheckName();
                boolean packetLevel = cn.startsWith("BadPackets") || cn.startsWith("Crash") || cn.startsWith("Exploit");
                boolean pingBlocksKick = !packetLevel && noPunishAbove > 0 && punishPing > noPunishAbove;
                // Fire ONCE per offense. add() returns true only the first time
                // we cross the threshold, so it works even if the VL jumps past
                // the exact max (e.g. 19 -> 22) or a flag() bypassed alert().
                if (!pingBlocksKick && graceElapsed && punishedChecks.add(punishKey)) {
                    for (String cmd : checkCfg.punishmentCommands) {
                        // MiniMessage tags -> legacy § codes; /kick takes plain text
                        // %ping%/%verbose% let a kick command spell out WHY (e.g.
                        // a Timer kick caused by a real ping spike) instead of just
                        // a generic check name — staff and the player both see it.
                        final String resolved = MessageUtil.toNativeText(
                                cmd.replace("%player%", player.user.getName())
                                   .replace("%check%", check.getCheckName())
                                   .replace("%vl%", String.valueOf(currentVL))
                                   .replace("%ping%", String.valueOf(punishPing))
                                   .replace("%verbose%", verbose == null ? "" : verbose));
                        GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                                GrimAPI.INSTANCE.getGrimPlugin(),
                                () -> GrimAPI.INSTANCE.getPlatformServer().dispatchCommand(
                                        GrimAPI.INSTANCE.getPlatformServer().getConsoleSender(), resolved)
                        );
                        ac.jester.anticheat.database.DatabaseManager.logPunishment(
                                player.uuid, player.user.getName(), check.getCheckName(), resolved);
                    }
                }
            } else if (currentVL <= 0) {
                // Streak fully decayed — reset everything so a future re-offense
                // punishes again and starts its own grace timer / ping tracking.
                punishedChecks.remove(punishKey);
                offenseFirstFlagMs.remove(punishKey);
                offensePeakPing.remove(punishKey);
            }
        }

        // Log to database for every violation (async, no TPS impact)
        ac.jester.anticheat.database.DatabaseManager.logViolation(
                player.uuid, player.user.getName(), check.getCheckName(), check.violations, verbose,
                player.getTransactionPing(), GrimAPI.INSTANCE.getPlatformServer().getTPS());

        // Store in per-player in-memory alert buffer (for /jester check)
        player.recentAlerts.add(check.getCheckName(), currentVL, verbose);

        if (!decision.shouldSend) return false;
        // ─────────────────────────────────────────────────────────────────────

        // Append suppressed count to verbose if alerts were batched
        String effectiveVerbose = verbose;
        if (decision.suppressedBefore > 0) {
            effectiveVerbose += " &8[+" + decision.suppressedBefore + " suppressed]";
        }
        final String finalVerbose = effectiveVerbose;

        // ── JesterAC: send the staff alert directly ───────────────────────────────
        // The per-check `checks:` config (dont-alert-until / alert-interval /
        // alert-cooldown-ms) is the alert authority. Previously the alert was
        // ONLY sent by the legacy punishments.yml groups below, whose thresholds
        // (e.g. 100 VL for MovementA) are far higher than the JesterAC kick
        // threshold (max-violations) — so players were kicked with no warning.
        String alertMsg = replaceAlertPlaceholders("[alert]", currentVL, check, finalVerbose);
        Component alertComponent = MessageUtil.miniMessage(alertMsg);

        // Apply the rich hover + teleport click to the WHOLE alert line (not just
        // the player name) so staff can hover anywhere on the message
        if (clickableAlerts) {
            Component hover = MessageUtil.miniMessage(
                    buildAlertHover(currentVL, checkCfg.maxViolations, check, finalVerbose));
            alertComponent = alertComponent
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/jester tp " + player.user.getName()));
        }

        // Verbose listeners (/jester verbose) get the alert too; exclude them
        // from the normal alert so they aren't messaged twice
        Set<@Nullable PlatformPlayer> verboseListeners = null;
        if (GrimAPI.INSTANCE.getAlertManager().hasVerboseListeners()) {
            verboseListeners = GrimAPI.INSTANCE.getAlertManager().sendVerbose(alertComponent, null);
        }

        if (testMode) { // secret test mode — only message the flagged player
            player.sendMessage(alertComponent);
        } else {
            GrimAPI.INSTANCE.getAlertManager().sendAlert(alertComponent, verboseListeners);
        }
        sentDebug = true;

        // Forward to other servers on the proxy network
        if (ProxyAlertMessenger.canSendAlerts()) {
            ProxyAlertMessenger.sendPluginMessage(
                    replaceAlertPlaceholders("[proxy]", currentVL, check, finalVerbose));
        }

        // ── Legacy punishments.yml groups: [log] history + custom commands ─────
        // [alert]/[proxy]/[webhook] are handled by the JesterAC path above, so they
        // are skipped here to avoid double alerts.
        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                final int vl = getViolations(group, check);
                final int violationCount = group.violations.size();
                for (ParsedCommand command : group.commands) {
                    if (command.command.equals("[alert]")
                            || command.command.equals("[proxy]")
                            || command.command.equals("[webhook]")) continue;

                    if (violationCount >= command.threshold) {
                        // 0 means execute once; any other number means every X interval
                        boolean inInterval = command.interval == 0 ? (command.executeCount == 0) : (violationCount % command.interval == 0);
                        if (inInterval) {
                            String cmd = replaceAlertPlaceholders(command.command, vl, check, finalVerbose);
                            CommandExecuteEvent executeEvent = new CommandExecuteEvent(player, check, finalVerbose, cmd);
                            GrimAPI.INSTANCE.getEventBus().post(executeEvent);
                            if (executeEvent.isCancelled()) continue;

                            if (command.command.equals("[log]")) {
                                int vls = (int) group.violations.values().stream().filter((e) -> e == check).count();
                                String verboseWithoutGl = finalVerbose.replaceAll(" /gl .*", "");
                                GrimAPI.INSTANCE.getViolationDatabaseManager().logAlert(player, verboseWithoutGl, check.getDisplayName(), vls);
                            } else {
                                GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(GrimAPI.INSTANCE.getGrimPlugin(), () ->
                                        GrimAPI.INSTANCE.getPlatformServer().dispatchCommand(
                                                GrimAPI.INSTANCE.getPlatformServer().getConsoleSender(), cmd));
                            }
                        }
                        command.executeCount++;
                    }
                }
            }
        }

        return sentDebug;
    }

    public void handleViolation(Check check) {
        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                long currentTime = System.currentTimeMillis();

                group.violations.put(currentTime, check);
                // Remove violations older than the defined time in the config
                group.violations.long2ObjectEntrySet().removeIf(time -> currentTime - time.getLongKey() > group.removeViolationsAfter);
            }
        }
    }

    private int getViolations(PunishGroup group, Check check) {
        int vl = 0;
        for (Check value : group.violations.values()) {
            if (value == check) vl++;
        }
        return vl;
    }
}

@RequiredArgsConstructor
class PunishGroup {
    public final List<AbstractCheck> checks;
    public final List<ParsedCommand> commands;
    public final Long2ObjectMap<Check> violations = new Long2ObjectOpenHashMap<>();
    public final int removeViolationsAfter; // time to remove violations after in milliseconds
}

@RequiredArgsConstructor
class ParsedCommand {
    public final int threshold;
    public final int interval;
    public final String command;
    public int executeCount;
}
