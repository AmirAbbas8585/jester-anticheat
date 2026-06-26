package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.Collection;

/**
 * /skyac stats — shows server-wide anticheat statistics.
 */
public class SkyStats implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("stats", "status")
                        .permission("skyac.stats")
                        .handler(this::handleStats)
        );
    }

    private void handleStats(CommandContext<Sender> context) {
        Sender sender = context.sender();

        Collection<GrimPlayer> players = GrimAPI.INSTANCE.getPlayerDataManager().getEntries();

        int total = players.size();
        int frozen = (int) players.stream()
                .filter(p -> p.uuid != null && GrimAPI.INSTANCE.getFreezeManager().isFrozen(p.uuid))
                .count();

        long totalViolations = players.stream()
                .flatMap(p -> p.checkManager.allChecks.values().stream())
                .filter(c -> c instanceof Check)
                .mapToLong(c -> (long) ((Check) c).violations)
                .sum();

        sender.sendMessage(MessageUtil.miniMessage(
                MessageUtil.replacePlaceholders(sender,
                        GrimAPI.INSTANCE.getConfigManager().getConfig()
                                .getStringElse("stats-header",
                                        "%prefix% &bSkyAntiCheat &8— &fServer Statistics"))));

        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>Players tracked: <white>%d</white>  Frozen: <red>%d</red>  Total VL: <yellow>%d</yellow>",
                total, frozen, totalViolations)));

        // Show top 5 players by combined VL
        sender.sendMessage(MessageUtil.miniMessage("<gray>Top flags:"));
        players.stream()
                .filter(p -> p.user != null)
                .sorted((a, b) -> {
                    double vlA = a.checkManager.allChecks.values().stream().filter(c -> c instanceof Check).mapToDouble(c -> ((Check) c).violations).sum();
                    double vlB = b.checkManager.allChecks.values().stream().filter(c -> c instanceof Check).mapToDouble(c -> ((Check) c).violations).sum();
                    return Double.compare(vlB, vlA);
                })
                .limit(5)
                .forEach(p -> {
                    double totalVL = p.checkManager.allChecks.values().stream().filter(c -> c instanceof Check).mapToDouble(c -> ((Check) c).violations).sum();
                    if (totalVL > 0) {
                        sender.sendMessage(MessageUtil.miniMessage(
                                String.format("<gray>  <white>%s</white> <dark_gray>—</dark_gray> <yellow>%.0f vl</yellow>",
                                        p.user.getName(), totalVL)));
                    }
                });
    }
}
