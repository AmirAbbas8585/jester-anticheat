package ac.jester.anticheat.events.packets;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;

public class PacketPlayerAbilities extends Check implements PacketCheck {
    private boolean lastSentPlayerCanFly = false;
    private int maxFlyingPing = 1000;

    public PacketPlayerAbilities(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ABILITIES) {
            WrapperPlayClientPlayerAbilities abilities = new WrapperPlayClientPlayerAbilities(event);
            boolean wasFlying = player.isFlying;
            player.isFlying = abilities.isFlying() && player.canFly;
            if (wasFlying != player.isFlying) {
                player.lastFlightToggleTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_ABILITIES) {
            WrapperPlayServerPlayerAbilities abilities = new WrapperPlayServerPlayerAbilities(event);
            player.sendTransaction();

            if (lastSentPlayerCanFly && !abilities.isFlightAllowed()) {
                int noFlying = player.lastTransactionSent.get();
                if (maxFlyingPing != -1) {
                    player.runNettyTaskInMs(() -> {
                        if (player.lastTransactionReceived.get() < noFlying) {
                            player.getSetbackTeleportUtil().executeViolationSetback();
                        }
                    }, maxFlyingPing);
                }
            }

            lastSentPlayerCanFly = abilities.isFlightAllowed();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                boolean wasCanFly = player.canFly;
                player.canFly = abilities.isFlightAllowed();
                player.isFlying = abilities.isFlying();
                if (wasCanFly != player.canFly) {
                    player.lastFlightToggleTime = System.currentTimeMillis();
                }
            });
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        maxFlyingPing = config.getIntElse("max-ping-out-of-flying", 1000);
    }
}
