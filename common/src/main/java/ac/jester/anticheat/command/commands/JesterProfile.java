package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class JesterProfile implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("profile")
                        .permission("jester.profile")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleProfile)
        );
    }

    private void handleProfile(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector target = context.get("target");

        PlatformPlayer targetPlatformPlayer = target.getSinglePlayer().getPlatformPlayer();
        if (Objects.requireNonNull(targetPlatformPlayer).isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender,"player-not-this-server", "%prefix% &cThis player isn't on this server!"));
            return;
        }

        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(targetPlatformPlayer.getUniqueId());
        if (grimPlayer == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cPlayer is exempt or offline!"));
            return;
        }

        for (String message : GrimAPI.INSTANCE.getConfigManager().getConfig().getStringList("profile")) {
            final Component component = MessageUtil.miniMessage(message);
            sender.sendMessage(MessageUtil.replacePlaceholders(grimPlayer, component));
        }
    }
}
