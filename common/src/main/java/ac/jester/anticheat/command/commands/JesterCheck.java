package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.manager.RecentAlerts;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * /jester check <player> [limit] — shows recent in-memory alerts for a player.
 *
 * Unlike /jester history (database), this shows live session data without
 * requiring the database to be enabled.
 */
public class JesterCheck implements BuildableCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("check")
                        .permission("jester.violations")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .optional("limit", IntegerParser.integerParser(1, 30))
                        .handler(this::handleCheck)
        );
    }

    private void handleCheck(CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector sel = context.get("target");
        PlatformPlayer target = sel.getSinglePlayer().getPlatformPlayer();
        int limit = context.getOrDefault("limit", 10);

        if (target == null || target.isExternalPlayer()) {
            sender.sendMessage(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(sender,
                            GrimAPI.INSTANCE.getConfigManager().getConfig()
                                    .getStringElse("player-not-found", "%prefix% &cPlayer not found!"))));
            return;
        }

        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
        if (grimPlayer == null) {
            sender.sendMessage(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(sender,
                            GrimAPI.INSTANCE.getConfigManager().getConfig()
                                    .getStringElse("player-not-found", "%prefix% &cPlayer not found!"))));
            return;
        }

        List<RecentAlerts.AlertEntry> alerts = grimPlayer.recentAlerts.getAll();

        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>Last %d alerts for <white>%s</white> <dark_gray>(session only)</dark_gray>:",
                Math.min(limit, alerts.size()), target.getName())));

        if (alerts.isEmpty()) {
            sender.sendMessage(MessageUtil.miniMessage("<gray>  <green>No alerts in this session.</green>"));
            return;
        }

        int shown = 0;
        for (RecentAlerts.AlertEntry entry : alerts) {
            if (shown >= limit) break;
            String time = TIME_FMT.format(Instant.ofEpochMilli(entry.timestamp()));
            String colorTag = entry.vl() >= 10 ? "red" : entry.vl() >= 3 ? "yellow" : "green";
            sender.sendMessage(MessageUtil.miniMessage(String.format(
                    "<dark_gray>[%s] <white>%s</white> <%s>x%d</%s> <gray>%s",
                    time, entry.checkName(), colorTag, entry.vl(), colorTag,
                    entry.verbose())));
            shown++;
        }
    }
}
