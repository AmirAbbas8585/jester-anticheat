package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "AutoClickerC", configName = "AutoClickerC",
        description = "Attacking on a fixed cadence while completely idle (AFK farm autoclicker)")
public final class AutoClickerC extends Check implements PacketCheck {
    private int minStationaryTicks = 6000;
    private int minAttacks = 40;
    private int reflagEvery = 20;

    private double anchorX = Double.NaN;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;

    private int stationaryTicks;
    private int attacks;

    public AutoClickerC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minStationaryTicks = Math.max(20, config.getIntElse("AutoClickerC.min-stationary-ticks", 6000));
        minAttacks = Math.max(1, config.getIntElse("AutoClickerC.min-attacks", 40));
        reflagEvery = Math.max(1, config.getIntElse("AutoClickerC.reflag-every-attacks", 20));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();

        if (type == PacketType.Play.Client.CLICK_WINDOW
                || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || type == PacketType.Play.Client.PLAYER_DIGGING
                || type == PacketType.Play.Client.HELD_ITEM_CHANGE
                || type == PacketType.Play.Client.CLOSE_WINDOW
                || type == PacketType.Play.Client.CHAT_MESSAGE) {
            reset();
            return;
        }

        if (isTickPacketIncludingNonMovement(type)) {
            trackIdle();
            return;
        }

        if (type != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (stationaryTicks < minStationaryTicks) return;

        attacks++;
        if (attacks >= minAttacks && (attacks - minAttacks) % reflagEvery == 0) {
            flagAndAlert(String.format("idle=%ds attacks=%d yaw=%.1f pitch=%.1f",
                    stationaryTicks / 20, attacks, anchorYaw, anchorPitch));
        }
    }

    private void trackIdle() {
        if (player.inVehicle()) {
            reset();
            return;
        }

        if (Double.isNaN(anchorX)) {
            anchor();
            return;
        }

        boolean frozen = player.x == anchorX && player.y == anchorY && player.z == anchorZ
                && player.yaw == anchorYaw && player.pitch == anchorPitch;
        if (!frozen) {
            reset();
            anchor();
            return;
        }
        stationaryTicks++;
    }

    private void anchor() {
        anchorX = player.x;
        anchorY = player.y;
        anchorZ = player.z;
        anchorYaw = player.yaw;
        anchorPitch = player.pitch;
    }

    private void reset() {
        stationaryTicks = 0;
        attacks = 0;
    }
}
