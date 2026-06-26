package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.manager.SetbackTeleportUtil;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

/**
 * /skyac setback <player> — force-setbacks a player to their last safe position.
 */
public class SkySetback implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("setback", "sb")
                        .permission("skyac.setback")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleSetback)
        );
    }

    private void handleSetback(CommandContext<Sender> context) {
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

        SetbackTeleportUtil setback = grimPlayer.getSetbackTeleportUtil();
        setback.executeViolationSetback();

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("setback-success",
                        "%prefix% &bSetback executed for &f%player%&b.")
                .replace("%player%", target.getName());
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
