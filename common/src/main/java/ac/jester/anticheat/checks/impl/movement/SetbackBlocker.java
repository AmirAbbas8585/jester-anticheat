package ac.jester.anticheat.checks.impl.movement;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

public class SetbackBlocker extends Check implements PacketCheck {
    public SetbackBlocker(GrimPlayer playerData) {
        super(playerData);
    }

    public void onPacketReceive(final PacketReceiveEvent event) {
        if (player.disableGrim)
            return;

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            if (player.getSetbackTeleportUtil().cheatVehicleInterpolationDelay > 0) {
                event.setCancelled(true);
            }
        }

        if (player.packetStateData.lastPacketWasTeleport) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
            }

            if (player.inVehicle() && event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION && !player.packetStateData.lastPacketWasTeleport) {
                event.setCancelled(true);
            }

            if (player.isInBed && new Vector3d(player.x, player.y, player.z).distanceSquared(player.bedPosition) > 1) {
                event.setCancelled(true);
            }

            if (player.compensatedEntities.self.isDead) {
                event.setCancelled(true);
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
            }

            if (!player.inVehicle()) {
                event.setCancelled(true);
            }

            if (player.isInBed) {
                event.setCancelled(true);
            }

            if (player.compensatedEntities.self.isDead) {
                event.setCancelled(true);
            }
        }
    }
}
