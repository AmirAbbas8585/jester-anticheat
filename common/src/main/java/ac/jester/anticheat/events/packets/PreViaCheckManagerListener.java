package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;

/**
 * Packet listener that runs BEFORE ViaVersion translates packets.
 *
 * Ported from Grim 2.3.74. Combat checks (Reach, PacketPlayerAttack) need to
 * see the raw client-side entity IDs before ViaVersion remaps them to server IDs.
 * Without this, cross-version players may bypass reach checks.
 */
public final class PreViaCheckManagerListener extends PacketListenerAbstract {

    public PreViaCheckManagerListener() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY
                && event.getConnectionState() != ConnectionState.CONFIGURATION) return;

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        player.checkManager.onPreViaPacketReceive(event);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY
                && event.getConnectionState() != ConnectionState.CONFIGURATION) return;

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        player.checkManager.onPreViaPacketSend(event);
    }
}
