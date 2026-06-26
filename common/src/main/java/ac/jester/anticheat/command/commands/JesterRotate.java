package ac.jester.anticheat.command.commands;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.command.BuildableCommand;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * /jester rotate <player> — snaps the target's view to a random direction.
 * Useful for staff to disrupt aim-assist clients: a legit player just looks
 * around again, while a locked aimbot keeps fighting from an impossible angle.
 */
public class JesterRotate implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("jester", "jac")
                        .literal("rotate")
                        .permission("jester.rotate")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(this::handleRotate)
        );
    }

    private void handleRotate(CommandContext<Sender> context) {
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

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float yaw = random.nextFloat() * 360f - 180f;   // -180 .. 180
        float pitch = random.nextFloat() * 120f - 60f;  // -60 .. 60 (avoid straight up/down)

        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager()
                .getPlayer(target.getUniqueId());

        // Keep position, only override rotation — X/Y/Z bits relative so delta=0 = don't move
        byte flagsMask = (byte) RelativeFlag.X.or(RelativeFlag.Y).or(RelativeFlag.Z).getMask();
        int teleportId = grimPlayer != null ? grimPlayer.lastTransactionSent.get() + 1 : 0;
        WrapperPlayServerPlayerPositionAndLook rotatePacket = new WrapperPlayServerPlayerPositionAndLook(
                0, 0, 0, yaw, pitch, flagsMask, teleportId, false
        );

        GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                    PacketEvents.getAPI().getPlayerManager()
                            .sendPacket(target.getNative(), rotatePacket);
                }
        );

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("rotate-sent",
                        "%prefix% &bRotated &f%player% &bto a random direction &7(yaw=%yaw% pitch=%pitch%)&b.")
                .replace("%player%", target.getName())
                .replace("%yaw%", String.format("%.1f", yaw))
                .replace("%pitch%", String.format("%.1f", pitch));
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
