package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * /skyac version — shows the plugin version. No remote update check.
 */
public class SkyVersion implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("version")
                        .permission("skyac.version")
                        .handler(this::handleVersion)
        );
    }

    private void handleVersion(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String current = GrimAPI.INSTANCE.getExternalAPI().getGrimVersion();
        sender.sendMessage(Component.text()
                .append(MessageUtil.miniMessage("%prefix%"))
                .append(Component.text(" Version: ").color(NamedTextColor.GRAY))
                .append(Component.text(current).color(NamedTextColor.AQUA))
                .build());
    }
}
