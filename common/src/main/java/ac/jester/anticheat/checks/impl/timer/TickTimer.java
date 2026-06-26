package ac.jester.anticheat.checks.impl.timer;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

@CheckData(name = "TickTimer", setback = 1)
public class TickTimer extends Check implements PacketCheck {

    private boolean receivedTickEnd = true;
    private int flyingPackets = 0;

    public TickTimer(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.supportsEndTick()) return;
        // Grace the flight (/fly) toggle window + post-join / resource-pack load —
        // packet ordering can briefly break across these transient states
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            receivedTickEnd = true;
            flyingPackets = 0;
            return;
        }
        if (isFlying(event.getPacketType()) && !player.packetStateData.lastPacketWasTeleport) {
            if (!receivedTickEnd && flagAndAlertWithSetback("type=flying, packets=" + flyingPackets)) {
                handleViolation();
            }
            receivedTickEnd = false;
            flyingPackets++;
        } else if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            receivedTickEnd = true;
            if (flyingPackets > 1 && flagAndAlertWithSetback("type=end, packets=" + flyingPackets)) {
                handleViolation();
            }
            flyingPackets = 0;
        }
    }

    private void handleViolation() {
        // Although we don't cancel the packet, this should be counted as an invalid packet.
        player.onPacketCancel();
    }
}
