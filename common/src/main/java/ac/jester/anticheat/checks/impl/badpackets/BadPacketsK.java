package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "BadPacketsK", description = "Sent spectate packets while not in spectator mode")
public class BadPacketsK extends Check implements PacketCheck {
    public BadPacketsK(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.SPECTATE
                && player.gamemode != GameMode.SPECTATOR
                && flagAndAlert() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
