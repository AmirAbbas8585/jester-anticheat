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
import com.github.retrooper.packetevents.protocol.player.GameMode;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class JesterSpectate implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("spectate")
                        .permission("jester.spectate")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleSpectate)
                        .apply(CloudCommandService.REQUIREMENT_FACTORY.create(PlayerSenderRequirement.PLAYER_SENDER_REQUIREMENT))
        );
    }

    private void handleSpectate(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector targetSelectorResults = context.getOrDefault("target", null);
        if (targetSelectorResults == null) return;

        PlatformPlayer targetPlatformPlayer = targetSelectorResults.getSinglePlayer().getPlatformPlayer();

        if (targetPlatformPlayer != null && targetPlatformPlayer.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "cannot-run-on-self", "%prefix% &cYou cannot use this command on yourself!"));
            return;
        }

        if (targetPlatformPlayer != null && targetPlatformPlayer.isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-this-server", "%prefix% &cThis player isn't on this server!"));
            return;
        }

        @NotNull PlatformPlayer platformPlayer = Objects.requireNonNull(sender.getPlatformPlayer());

        // hide player from tab list
        if (GrimAPI.INSTANCE.getSpectateManager().enable(platformPlayer)) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "spectate-return", "<click:run_command:/jester stopspectating><hover:show_text:\"/jester stopspectating\">\n%prefix% &fClick here to return to previous location\n</hover></click>"));
        }

        // Spectator BEFORE the teleport so the staff member is never briefly
        // solid and vulnerable at the destination...
        platformPlayer.setGameMode(GameMode.SPECTATOR);
        platformPlayer.teleportAsync(Objects.requireNonNull(targetPlatformPlayer).getLocation())
                .thenAccept(success -> {
                    if (!success) {
                        // Don't strand them mid-spectate with a failed teleport.
                        GrimAPI.INSTANCE.getSpectateManager().disable(platformPlayer, false);
                        sender.sendMessage(MessageUtil.getParsedComponent(sender, "spectate-teleport-failed",
                                "%prefix% &cCouldn't teleport to that player, try again."));
                        return;
                    }
                    // ...and re-assert it AFTER, because a cross-world teleport
                    // makes the server re-apply the destination world's gamemode.
                    // Anything doing per-world gamemodes (Multiverse's
                    // enforce-gamemode, lobby/world-manager plugins) then drops
                    // the staff member back into survival the moment they land —
                    // which is exactly what happened spectating from the overworld
                    // into the Nether. Setting it once before the teleport cannot
                    // survive that; setting it again after the world change does.
                    if (platformPlayer.getGameMode() != GameMode.SPECTATOR) {
                        platformPlayer.setGameMode(GameMode.SPECTATOR);
                    }
                });
    }
}
