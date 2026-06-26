package ac.jester.anticheat.command.commands;

import ac.grim.grimac.api.AbstractCheck;
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
import org.incendo.cloud.parser.standard.StringParser;

public class JesterClearViolations implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        // /jester clearviolations <player> [check]
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("clearviolations", "clearvl", "cvl")
                        .permission("jester.clearviolations")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .optional("check", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                        .handler(this::handleClear)
        );
    }

    private void handleClear(CommandContext<Sender> context) {
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

        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager()
                .getPlayer(target.getUniqueId());
        if (grimPlayer == null) {
            sender.sendMessage(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(sender,
                            GrimAPI.INSTANCE.getConfigManager().getConfig()
                                    .getStringElse("player-not-found", "%prefix% &cPlayer not found!"))));
            return;
        }

        String checkFilter = context.getOrDefault("check", null);
        int cleared = 0;

        for (AbstractCheck abstractCheck : grimPlayer.checkManager.allChecks.values()) {
            if (checkFilter == null
                    || abstractCheck.getCheckName().toLowerCase().contains(checkFilter.toLowerCase())
                    || abstractCheck.getAlternativeName().toLowerCase().contains(checkFilter.toLowerCase())) {
                if (abstractCheck instanceof Check check) {
                    check.violations = 0;
                }
                cleared++;
            }
        }

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("clearviolations-success",
                        "%prefix% &bCleared &f%count% &bcheck(s) for &f%player%&b.")
                .replace("%player%", target.getName())
                .replace("%count%", String.valueOf(cleared));
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
