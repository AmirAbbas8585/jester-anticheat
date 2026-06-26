package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.Comparator;
import java.util.List;

/**
 * /skyac violations <player> — shows per-check VL breakdown for an online player.
 */
public class SkyViolations implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("violations", "vl")
                        .permission("skyac.violations")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleViolations)
        );
    }

    private void handleViolations(CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector sel = context.get("target");
        PlatformPlayer target = sel.getSinglePlayer().getPlatformPlayer();

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

        List<Check> checks = grimPlayer.checkManager.allChecks.values().stream()
                .filter(c -> c instanceof Check)
                .map(c -> (Check) c)
                .filter(c -> c.violations >= 0.1)
                .sorted(Comparator.comparingDouble(Check::getViolations).reversed())
                .toList();

        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>Violations for <white>%s</white>:", target.getName())));

        if (checks.isEmpty()) {
            sender.sendMessage(MessageUtil.miniMessage("<gray>  <green>No active violations.</green>"));
            return;
        }

        for (Check check : checks) {
            double vl = check.violations;
            String colorTag = vl >= 10 ? "red" : vl >= 3 ? "yellow" : "green";
            sender.sendMessage(MessageUtil.miniMessage(String.format(
                    "<gray>  <white>%-22s</white> <%s>%.2f vl</%s>",
                    check.getCheckName(), colorTag, vl, colorTag)));
        }
    }
}
