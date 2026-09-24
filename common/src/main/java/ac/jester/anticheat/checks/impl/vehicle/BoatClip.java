package ac.jester.anticheat.checks.impl.vehicle;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "BoatClip", configName = "BoatClip",
        description = "Vehicle moving teleport-sized distances per packet (clip)")
public final class BoatClip extends Check implements PacketCheck {
    private double maxDelta = 5.0;
    private int minConsecutive = 2;

    private Vector3d lastPos = null;
    private Object lastVehicle = null;
    private int consecutiveOversized = 0;

    public BoatClip(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxDelta = config.getDoubleElse("BoatClip.max-delta", 5.0);
        minConsecutive = config.getIntElse("BoatClip.min-consecutive", 2);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type == PacketType.Play.Server.VEHICLE_MOVE
                || type == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK
                || type == PacketType.Play.Server.SET_PASSENGERS
                || type == PacketType.Play.Server.RESPAWN) {
            reset();
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            if (isOursOrVehicle(new com.github.retrooper.packetevents.wrapper.play.server
                    .WrapperPlayServerEntityTeleport(event).getEntityId())) reset();
        } else if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            if (isOursOrVehicle(new com.github.retrooper.packetevents.wrapper.play.server
                    .WrapperPlayServerEntityPositionSync(event).getId())) reset();
        }
    }

    private boolean isOursOrVehicle(int entityId) {
        if (entityId == player.entityID) return true;
        Object riding = player.compensatedEntities.self.getRiding();
        return riding != null && player.compensatedEntities.entityMap.get(entityId) == riding;
    }

    private void reset() {
        lastPos = null;
        consecutiveOversized = 0;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;

        Object riding = player.compensatedEntities.self.getRiding();
        if (riding == null) {
            reset();
            lastVehicle = null;
            return;
        }
        if (riding != lastVehicle) {
            reset();
            lastVehicle = riding;
        }

        WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
        Vector3d pos = move.getPosition();

        if (lastPos != null) {
            double dx = pos.getX() - lastPos.getX();
            double dy = pos.getY() - lastPos.getY();
            double dz = pos.getZ() - lastPos.getZ();
            double delta = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (delta > maxDelta) {
                consecutiveOversized++;
                if (consecutiveOversized >= minConsecutive
                        && player.isTickingReliablyFor(5) && player.getTransactionPing() < 500) {
                    flagAndAlert(String.format("delta=%.2f max=%.1f consecutive=%d ping=%dms",
                            delta, maxDelta, consecutiveOversized, player.getTransactionPing()));
                    consecutiveOversized = 0;
                }
            } else {
                consecutiveOversized = 0;
            }
        }

        lastPos = pos;
    }
}
