package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.impl.combat.AutoClickerA;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public class SkyCPS implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("cps")
                        .permission("skyac.cps")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleCPS)
        );
    }

    private void handleCPS(CommandContext<Sender> context) {
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

        AutoClickerA check = (AutoClickerA) grimPlayer.checkManager.allChecks.get(AutoClickerA.class);
        double cps = check != null ? check.getLastCps() : 0.0;

        // Determine color based on CPS threshold
        String cpsColor = cps > 20 ? "&c" : cps > 14 ? "&e" : "&a";

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("cps-info",
                        "%prefix% &f%player% &b— CPS: " + cpsColor + "%.1f &8(avg over last 20 clicks)")
                .replace("%player%", target.getName())
                .replace("%.1f", String.format("%.1f", cps));
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
