package ac.jester.anticheat.checks.impl.flight;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

public class FlightA extends Check implements PacketCheck {
    public FlightA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.isFlying) {
            flag();
        }
    }
}
