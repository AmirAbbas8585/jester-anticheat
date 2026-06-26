package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.command.CloudCommandService;
import ac.jester.anticheat.command.requirements.PlayerSenderRequirement;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.Objects;

/**
 * Silent staff teleport to a player.
 * Unlike /tp this stays in the player's current gamemode and doesn't show particles/sounds.
 */
public class JesterTP implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("tp", "goto")
                        .permission("jester.tp")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleTP)
                        .apply(CloudCommandService.REQUIREMENT_FACTORY.create(PlayerSenderRequirement.PLAYER_SENDER_REQUIREMENT))
        );
    }

    private void handleTP(CommandContext<Sender> context) {
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

        PlatformPlayer self = Objects.requireNonNull(sender.getPlatformPlayer());

        if (self.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(sender,
                            GrimAPI.INSTANCE.getConfigManager().getConfig()
                                    .getStringElse("cannot-run-on-self", "%prefix% &cYou cannot use this on yourself!"))));
            return;
        }

        self.teleportAsync(target.getLocation());

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("tp-success", "%prefix% &bTeleported to &f%player%&b.")
                .replace("%player%", target.getName());
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
