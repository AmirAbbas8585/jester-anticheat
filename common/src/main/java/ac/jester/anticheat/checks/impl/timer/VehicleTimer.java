package ac.jester.anticheat.checks.impl.timer;

import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

@CheckData(name = "VehicleTimer", setback = 10)
public class VehicleTimer extends Timer {
    private boolean isDummy = false;

    public VehicleTimer(GrimPlayer player) {
        super(player);
    }

    @Override
    public boolean shouldCountPacketForTimer(PacketTypeCommon packetType) {
        if (player.packetStateData.lastPacketWasTeleport) return false;

        if (packetType == PacketType.Play.Client.VEHICLE_MOVE) {
            isDummy = false;
            return true;
        }

        if (packetType == PacketType.Play.Client.STEER_VEHICLE) {
            if (isDummy) {
                return true;
            }
            isDummy = true;
        }

        return false;
    }
}
