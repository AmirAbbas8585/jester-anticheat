package ac.jester.anticheat.checks.impl.vehicle;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

/**
 * BoatClip — vehicle teleport-sized movement (entity clip / boat blink).
 *
 * The fastest legitimate vehicle movement is a boat racing downhill on blue
 * ice at roughly 3.5 blocks per tick. Clip/blink hacks move the vehicle many
 * blocks in a single VEHICLE_MOVE packet to pass through walls.
 *
 * A single oversized move can be a server-initiated vehicle teleport echoing
 * back, so two consecutive oversized moves are required.
 */
@CheckData(name = "BoatClip", configName = "BoatClip",
        description = "Vehicle moving teleport-sized distances per packet (clip)")
public final class BoatClip extends Check implements PacketCheck {

    private double maxDelta = 5.0;
    private int minConsecutive = 2;

    private Vector3d lastPos = null;
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
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;

        if (player.compensatedEntities.self.getRiding() == null) {
            lastPos = null;
            consecutiveOversized = 0;
            return;
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
