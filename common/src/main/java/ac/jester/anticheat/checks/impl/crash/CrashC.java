package ac.jester.anticheat.checks.impl.crash;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "CrashC", description = "Sent non-finite position or rotation")
public class CrashC extends Check implements PacketCheck {
    public CrashC(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            Location pos = flying.getLocation();

            boolean invalidPosition = flying.hasPositionChanged()
                    && (!Double.isFinite(pos.getX()) || !Double.isFinite(pos.getY()) || !Double.isFinite(pos.getZ()));
            boolean invalidRotation = flying.hasRotationChanged()
                    && (!Float.isFinite(pos.getYaw()) || !Float.isFinite(pos.getPitch()));
            if (!invalidPosition && !invalidRotation) return;

            flagAndCancel(event, pos);
        } else if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            if (Double.isFinite(move.getPosition().getX()) && Double.isFinite(move.getPosition().getY())
                    && Double.isFinite(move.getPosition().getZ())
                    && Float.isFinite(move.getYaw()) && Float.isFinite(move.getPitch())) {
                return;
            }

            flagAndCancel(event, new Location(move.getPosition(), move.getYaw(), move.getPitch()));
        }
    }

    private void flagAndCancel(PacketReceiveEvent event, Location pos) {
        flagAndAlert("xyzYP=" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ", " + pos.getYaw() + ", " + pos.getPitch());
        player.getSetbackTeleportUtil().executeViolationSetback();
        event.setCancelled(true);
        player.onPacketCancel();
    }
}
