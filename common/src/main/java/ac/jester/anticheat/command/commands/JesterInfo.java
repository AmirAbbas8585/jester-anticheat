package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

/**
 * /jester info <player> — shows detailed real-time player state for staff investigation.
 */
public class JesterInfo implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("info")
                        .permission("jester.info")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleInfo)
        );
    }

    private void handleInfo(CommandContext<Sender> context) {
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

        GrimPlayer p = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
        if (p == null) {
            sender.sendMessage(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(sender,
                            GrimAPI.INSTANCE.getConfigManager().getConfig()
                                    .getStringElse("player-not-found", "%prefix% &cPlayer not found!"))));
            return;
        }

        String name = target.getName();
        int ping = p.getTransactionPing();
        String clientVer = p.getClientVersion().getReleaseName();
        String gamemode = p.gamemode != null ? p.gamemode.name() : "UNKNOWN";
        boolean onGround = p.onGround;
        boolean clientGround = p.clientClaimsLastOnGround;
        boolean sprinting = p.isSprinting;
        boolean sneaking = p.isSneaking;
        boolean gliding = p.isGliding;
        double speed = p.speed;
        int food = p.food;
        float health = p.packetStateData.lastHealth;
        boolean frozen = GrimAPI.INSTANCE.getFreezeManager().isFrozen(p.uuid);

        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>Player info: <white>%s</white>", name)));
        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>  Ping: <white>%dms</white>  Client: <white>%s</white>  Gamemode: <aqua>%s</aqua>",
                ping, clientVer, gamemode)));
        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>  Pos: <white>%.2f, %.2f, %.2f</white>  Yaw/Pitch: <white>%.1f / %.1f</white>",
                p.x, p.y, p.z, p.yaw, p.pitch)));
        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>  OnGround: <white>%s</white> (client: <white>%s</white>)  Speed: <white>%.4f</white>",
                onGround, clientGround, speed)));
        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>  Sprint: <white>%s</white>  Sneak: <white>%s</white>  Glide: <white>%s</white>",
                sprinting, sneaking, gliding)));
        sender.sendMessage(MessageUtil.miniMessage(String.format(
                "<gray>  HP: <red>%.1f</red>  Food: <yellow>%d</yellow>  Frozen: <%s>%s</%s>",
                health, food, frozen ? "red" : "green", frozen, frozen ? "red" : "green")));
    }
}
