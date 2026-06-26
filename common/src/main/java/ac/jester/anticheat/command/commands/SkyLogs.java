package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.hooks.GuiProvider;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

/**
 * /skyac logs <player> — opens the violation log GUI.
 *
 * The target is a plain string (not an online-player selector) so staff can
 * inspect players who already left; the data comes from the database.
 */
public class SkyLogs implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("logs")
                        .permission("skyac.logs")
                        .required("target", StringParser.stringParser())
                        .handler(this::handleLogs)
        );
    }

    private void handleLogs(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String targetName = context.get("target");

        if (sender.getPlatformPlayer() == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "run-as-player",
                    "%prefix% &cThis command can only be used by players!"));
            return;
        }

        boolean opened = GuiProvider.safe().openViolationLog(
                sender.getPlatformPlayer().getUniqueId(), targetName);
        if (!opened) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "logs-unavailable",
                    "%prefix% &cLog GUI unavailable — make sure the database is enabled."));
        }
    }
}
