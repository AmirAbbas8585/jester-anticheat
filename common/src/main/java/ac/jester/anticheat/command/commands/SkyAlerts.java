package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SkyAlerts implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("alerts", Description.of("Toggle alerts for the sender"))
                        .permission("skyac.alerts")
                        .handler(this::handleAlerts)
        );
    }

    // Suppress warning as we've already checked sender is not console
    private void handleAlerts(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (sender.isPlayer()) {
            GrimAPI.INSTANCE.getAlertManager().toggleAlerts(Objects.requireNonNull(context.sender().getPlatformPlayer()), false);
        } else if (sender.isConsole()) {
            GrimAPI.INSTANCE.getAlertManager().toggleConsoleAlerts();
        }
    }
}
