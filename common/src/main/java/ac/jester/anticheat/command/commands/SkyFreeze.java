package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.UUID;

/**
 * /skyac freeze <player> — toggle: freezes the target if free, unfreezes if frozen.
 * (The old separate /skyac unfreeze is kept as an alias of the same toggle.)
 */
public class SkyFreeze implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("freeze", "fr", "unfreeze", "unfr", "ufr")
                        .permission("skyac.freeze")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleToggle)
        );
    }

    private void handleToggle(CommandContext<Sender> context) {
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

        UUID targetUUID = target.getUniqueId();

        if (GrimAPI.INSTANCE.getFreezeManager().isFrozen(targetUUID)) {
            unfreeze(sender, target, targetUUID);
        } else {
            freeze(sender, target, targetUUID);
        }
    }

    private void freeze(Sender sender, PlatformPlayer target, UUID targetUUID) {
        UUID senderUUID = sender.isPlayer() ? sender.getUniqueId() : null;
        GrimAPI.INSTANCE.getFreezeManager().freeze(targetUUID, senderUUID);

        // Notify staff
        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("freeze-frozen",
                        "%prefix% &f%player% &bhas been &cfrozen&b.")
                .replace("%player%", target.getName());
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));

        // Notify target
        String targetMsg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("freeze-target-notify",
                        "%prefix% &cYou have been &ffrozen &cby a staff member. Do not log out.");
        target.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, targetMsg)));
    }

    private void unfreeze(Sender sender, PlatformPlayer target, UUID targetUUID) {
        GrimAPI.INSTANCE.getFreezeManager().unfreeze(targetUUID);

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("freeze-unfrozen",
                        "%prefix% &f%player% &bhas been &aunfrozen&b.")
                .replace("%player%", target.getName());
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));

        String targetMsg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("freeze-target-unfreeze",
                        "%prefix% &aYou have been &funfrozen&a.");
        target.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, targetMsg)));
    }
}
