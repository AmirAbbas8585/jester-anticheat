package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "BadPacketsZ", experimental = true)
public class BadPacketsZ extends Check implements PacketCheck {
    private boolean sent;

    public BadPacketsZ(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            sent = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            // Real logs showed this flagging continuously (every ~2s, sustained
            // for 40+ seconds straight) for a player riding/steering a boat,
            // with completely normal TPS/ping throughout. Vehicle controls send
            // PLAYER_INPUT for boat steering on top of (or instead of) normal
            // movement input, which doesn't follow the "exactly once per tick"
            // assumption this check relies on for plain on-foot movement.
            if (player.inVehicle()) {
                sent = false;
                return;
            }

            if (sent) {
                flagAndAlert();
            }

            sent = true;
        }
    }
}
