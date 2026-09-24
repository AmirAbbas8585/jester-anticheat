package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

public class JesterHelp implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("help", Description.of("Display help information"))
                        .permission("jester.help")
                        .handler(this::handleHelp)
        );
    }

    private void handleHelp(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();

        for (String string : GrimAPI.INSTANCE.getConfigManager().getConfig().getStringList("help")) {
            if (string == null) continue;
            string = MessageUtil.replacePlaceholders(sender, string);
            sender.sendMessage(MessageUtil.miniMessage(string));
        }
    }
}
