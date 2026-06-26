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
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.FloatParser;

public class SkyKnockback implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("skyac", "sac", "jester", "jac")
                        .literal("knockback", "kb")
                        .permission("skyac.knockback")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .optional("strength", FloatParser.floatParser(0.1f, 5.0f))
                        .handler(this::handleKB)
        );
    }

    private void handleKB(CommandContext<Sender> context) {
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

        float strength = context.getOrDefault("strength", 1.0f);

        // Send velocity packet directly via PacketEvents — bypasses Bukkit's event system
        // and is guaranteed to reach the client even if the player has nosetback perm
        int entityId = PacketEvents.getAPI().getPlayerManager()
                .getUser(target.getNative()).getEntityId();

        // Push the target away in the direction they're currently facing (so they
        // visibly fly backward, like real combat knockback) plus a vertical arc.
        // Vanilla level-0 KB is roughly 0.4 horizontal / 0.4 vertical — scale both
        // by strength so the test command actually demonstrates a knockback.
        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
        double yawRad = Math.toRadians(grimPlayer != null ? grimPlayer.yaw : 0f);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        WrapperPlayServerEntityVelocity velocityPacket = new WrapperPlayServerEntityVelocity(
                entityId,
                new Vector3d(dirX * strength * 0.4, strength * 0.4, dirZ * strength * 0.4)
        );

        GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                    PacketEvents.getAPI().getPlayerManager()
                            .sendPacket(target.getNative(), velocityPacket);
                }
        );

        String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                .getStringElse("kb-sent",
                        "%prefix% &bKnockback &f(strength=&c%.1f&f) &bsent to &f%player%&b.")
                .replace("%player%", target.getName())
                .replace("%.1f", String.format("%.1f", strength));
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }
}
