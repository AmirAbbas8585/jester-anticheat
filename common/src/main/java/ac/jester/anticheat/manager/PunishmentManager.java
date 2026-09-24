package ac.jester.anticheat.manager;

import java.util.List;
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
    private final java.util.Set<String> punishedChecks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Long> offenseFirstFlagMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> offensePeakPing = new java.util.concurrent.ConcurrentHashMap<>();

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
                    for (AbstractCheck check : player.checkManager.allChecks.values()) {
                        if (check.getCheckName() != null &&
                                (check.getCheckName().toLowerCase(Locale.ROOT).contains(command)
                                        || check.getAlternativeName().toLowerCase(Locale.ROOT).contains(command))) {
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
        return MessageUtil.replacePlaceholders(player, original
                .replace("[alert]", alertString)
                .replace("[proxy]", proxyAlertString)
                .replace("%player%", player.user.getName())
                .replace("%check_name%", check.getDisplayName())
                .replace("%experimental%", "")
                .replace("%vl%", Integer.toString(vl))
                .replace("%description%", check.getDescription())
        ).replace("%verbose%", MiniMessage.miniMessage().escapeTags(verbose));
    }

    private String buildAlertHover(int vl, int maxVl, Check check, String verbose) {
        MiniMessage mm = MiniMessage.miniMessage();
        String brand = mm.escapeTags((player.getBrand() != null ? player.getBrand() : "unknown").replace("'", ""));
        String safeVerbose = mm.escapeTags(verbose == null ? "" : verbose).replace("'", "");
        String description = mm.escapeTags(ac.jester.anticheat.utils.anticheat.FlagExplainer
                .explain(check.getConfigName(), verbose, vl, player.getTransactionPing(),
                        GrimAPI.INSTANCE.getPlatformServer().getTPS())
                .replace("'", ""));
        var clientVersion = player.getClientVersion();
        String version = clientVersion != null ? clientVersion.getReleaseName() : "?";

        List<String> lines = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringListElse("alert-hover", DEFAULT_ALERT_HOVER);
        if (lines.isEmpty()) lines = DEFAULT_ALERT_HOVER;

        double tps = GrimAPI.INSTANCE.getPlatformServer().getTPS();
        return String.join("<newline>", lines).stripTrailing()
                .replace("%player%", player.user.getName())
                .replace("%brand%", brand)
                .replace("%client_version%", version)
                .replace("%check_name%", check.getDisplayName())
                .replace("%vl%", String.valueOf(vl))
                .replace("%max_vl%", String.valueOf(maxVl))
                .replace("%ping%", String.valueOf(player.getTransactionPing()))
                .replace("%tps%", String.format("%.1f", tps))
                .replace("%x%", String.format("%.0f", player.x))
                .replace("%y%", String.format("%.0f", player.y))
                .replace("%z%", String.format("%.0f", player.z))
                .replace("%gamemode%", player.gamemode != null ? player.gamemode.name() : "UNKNOWN")
                .replace("%info%", description)
                .replace("%verbose%", safeVerbose);
    }

    private static final List<String> DEFAULT_ALERT_HOVER = List.of(
            "<color:#c19a6b>•</color> <white>Player:</white> <color:#a9c8ff>%player%</color>",
            "<color:#c19a6b>•</color> <white>Brand:</white> <color:#a9c8ff>%brand%</color> <color:#c19a6b>(%client_version%)</color>",
            "<color:#c19a6b>•</color> <white>Check:</white> <color:#ff5348>%check_name%</color>",
            "<color:#c19a6b>•</color> <white>Violations:</white> <color:#ff5348>x%vl%</color> <white>/</white> <color:#c19a6b>%max_vl%</color>",
            "<color:#c19a6b>•</color> <white>Ping:</white> <color:#c19a6b>%ping%ms</color>  <white>TPS:</white> <color:#c19a6b>%tps%</color>",
            "<color:#c19a6b>•</color> <white>Position:</white> <color:#a9c8ff>%x%, %y%, %z%</color> <dark_gray>(</dark_gray><color:#a9c8ff>%gamemode%</color><dark_gray>)</dark_gray>",
            "",
            "<white>Info:</white> <color:#a9c8ff>%info%</color>",
            "<white>Verbose:</white> <color:#a9c8ff>%verbose%</color>",
            "",
            "<color:#c19a6b>•</color> <white>Click to teleport</white>");

    private static void executePunishment(GrimPlayer player, Check check,
                                          JesterCheckConfig.CheckSettings checkCfg,
                                          int currentVL, int punishPing, String verbose) {
        for (String cmd : checkCfg.punishmentCommands) {
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

    public boolean handleAlert(GrimPlayer player, String verbose, Check check) {
        boolean sentDebug = false;

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

        boolean allowedToPunish = checkCfg.punishable
                && (!check.isExperimental() || JesterCheckConfig.isExperimentalChecksPunishable());
        if (allowedToPunish && !checkCfg.punishmentCommands.isEmpty()) {
            final String punishKey = check.getConfigName();
            final long now = System.currentTimeMillis();
            if (currentVL > 0) {
                offenseFirstFlagMs.putIfAbsent(punishKey, now);
                offensePeakPing.merge(punishKey, player.getTransactionPing(), Math::max);
            }
            if (currentVL >= checkCfg.maxViolations) {
                long streakStart = offenseFirstFlagMs.getOrDefault(punishKey, now);
                boolean graceElapsed = now - streakStart >= checkCfg.punishGraceMs;
                int punishPing = offensePeakPing.getOrDefault(punishKey, player.getTransactionPing());
                int noPunishAbove = GrimAPI.INSTANCE.getConfigManager().getConfig()
                        .getIntElse("high-ping.no-punish-above-ms", 400);
                String cn = check.getCheckName();
                boolean packetLevel = cn.startsWith("BadPackets") || cn.startsWith("Crash") || cn.startsWith("Exploit");
                boolean pingBlocksKick = !packetLevel && noPunishAbove > 0
                        && player.getTransactionPing() > noPunishAbove;
                if (!pingBlocksKick && punishedChecks.add(punishKey)) {
                    if (graceElapsed) {
                        executePunishment(player, check, checkCfg, currentVL, punishPing, verbose);
                    } else {
                        long remainingMs = checkCfg.punishGraceMs - (now - streakStart);
                        long delayTicks = Math.max(1L, (remainingMs + 49) / 50);
                        GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().runDelayed(
                                GrimAPI.INSTANCE.getGrimPlugin(),
                                () -> {
                                    if (player.disableGrim) return;
                                    JesterCheckConfig.CheckSettings live =
                                            JesterCheckConfig.get(check.getConfigName());
                                    int vlNow = (int) check.violations;
                                    int liveNoPunishAbove = GrimAPI.INSTANCE.getConfigManager().getConfig()
                                            .getIntElse("high-ping.no-punish-above-ms", 400);
                                    boolean decline = !live.enabled || !live.punishable
                                            || vlNow < live.maxViolations
                                            || (!packetLevel && liveNoPunishAbove > 0
                                                && player.getTransactionPing() > liveNoPunishAbove);
                                    if (decline) {
                                        punishedChecks.remove(punishKey);
                                        return;
                                    }
                                    executePunishment(player, check, live, vlNow,
                                            offensePeakPing.getOrDefault(punishKey, player.getTransactionPing()),
                                            verbose);
                                },
                                delayTicks);
                    }
                }
            } else if (currentVL <= 0) {
                punishedChecks.remove(punishKey);
                offenseFirstFlagMs.remove(punishKey);
                offensePeakPing.remove(punishKey);
            }
        }

        ac.jester.anticheat.database.DatabaseManager.logViolation(
                player.uuid, player.user.getName(), check.getCheckName(), check.violations, verbose,
                player.getTransactionPing(), GrimAPI.INSTANCE.getPlatformServer().getTPS());

        player.recentAlerts.add(check.getCheckName(), currentVL, verbose);

        if (!decision.shouldSend) return false;

        String effectiveVerbose = verbose;
        if (decision.suppressedBefore > 0) {
            effectiveVerbose += " &8[+" + decision.suppressedBefore + " suppressed]";
        }
        final String finalVerbose = effectiveVerbose;

        String alertMsg = replaceAlertPlaceholders("[alert]", currentVL, check, finalVerbose);
        Component alertComponent = MessageUtil.miniMessage(alertMsg);

        if (clickableAlerts) {
            Component hover = MessageUtil.miniMessage(
                    buildAlertHover(currentVL, checkCfg.maxViolations, check, finalVerbose));
            alertComponent = alertComponent
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/jester tp " + player.user.getName()));
        }

        Set<@Nullable PlatformPlayer> verboseListeners = null;
        if (GrimAPI.INSTANCE.getAlertManager().hasVerboseListeners()) {
            verboseListeners = GrimAPI.INSTANCE.getAlertManager().sendVerbose(alertComponent, null);
        }

        if (testMode) {
            player.sendMessage(alertComponent);
        } else {
            GrimAPI.INSTANCE.getAlertManager().sendAlert(alertComponent, verboseListeners);
        }
        sentDebug = true;

        if (ProxyAlertMessenger.canSendAlerts()) {
            ProxyAlertMessenger.sendPluginMessage(
                    replaceAlertPlaceholders("[proxy]", currentVL, check, finalVerbose));
        }

        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                final int vl = getViolations(group, check);
                final int violationCount = group.violations.size();
                for (ParsedCommand command : group.commands) {
                    if (command.command.equals("[alert]")
                            || command.command.equals("[proxy]")
                            || command.command.equals("[webhook]")) continue;

                    if (violationCount >= command.threshold) {
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
    public final int removeViolationsAfter;
}

@RequiredArgsConstructor
class ParsedCommand {
    public final int threshold;
    public final int interval;
    public final String command;
    public int executeCount;
}
