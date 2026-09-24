package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PacketPlayerJoinQuit extends PacketListenerAbstract {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Login.Server.LOGIN_SUCCESS) {
            event.getTasksAfterSend().add(() -> GrimAPI.INSTANCE.getPlayerDataManager().addUser(event.getUser()));
        }
    }

    @Override
    public void onUserConnect(UserConnectEvent event) {
        if (event.getUser().getConnectionState() == ConnectionState.PLAY && !GrimAPI.INSTANCE.getPlayerDataManager().exemptUsers.contains(event.getUser())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        Object nativePlayerObject = Objects.requireNonNull(event.getPlayer());

        @NotNull PlatformPlayer platformPlayer = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromNativePlayerType(nativePlayerObject);

        if (GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("debug-pipeline-on-join", false)) {
            LogUtil.info("Pipeline: " + ChannelHelper.pipelineHandlerNamesAsString(event.getUser().getChannel()));
        }
        if (platformPlayer.hasPermission("jester.alerts", false)
                && GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("toggle-alerts-on-join", true)) {
            GrimAPI.INSTANCE.getAlertManager().setAlertsEnabled(platformPlayer, true, platformPlayer.hasPermission("jester.alerts.silent", false));
        }
        if (platformPlayer.hasPermission("jester.verbose.enable-on-join", false) && platformPlayer.hasPermission("jester.verbose", false)) {
            GrimAPI.INSTANCE.getAlertManager().setVerboseEnabled(platformPlayer, true, platformPlayer.hasPermission("jester.verbose.silent", false));
        }
        if (platformPlayer.hasPermission("jester.brand", false)) {
            GrimAPI.INSTANCE.getAlertManager().setBrandsEnabled(platformPlayer, true, platformPlayer.hasPermission("jester.brand.silent", false));
        }
        if (platformPlayer.hasPermission("jester.spectate") && GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("spectators.hide-regardless", false)) {
            GrimAPI.INSTANCE.getSpectateManager().onLogin(platformPlayer.getUniqueId());
        }

        if (ac.jester.anticheat.manager.UpdateState.isAvailable()) {
            String notifyPerm = GrimAPI.INSTANCE.getConfigManager().getConfig()
                    .getStringElse("update-checker.notify-permission", "jester.update");
            if (platformPlayer.hasPermission(notifyPerm, false)) {
                String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                        .getStringElse("update-available",
                                "%prefix% <yellow>A new version is available: <green>%latest%</green> <gray>(running <red>%current%</red>)")
                        .replace("%latest%", ac.jester.anticheat.manager.UpdateState.latest())
                        .replace("%current%", ac.jester.anticheat.manager.UpdateState.current());
                platformPlayer.sendMessage(ac.jester.anticheat.utils.anticheat.MessageUtil.miniMessage(msg));
            }
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        GrimAPI.INSTANCE.getPlayerDataManager().onDisconnect(event.getUser());
    }
}
