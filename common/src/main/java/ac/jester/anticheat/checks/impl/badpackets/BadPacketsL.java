package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.Locale;

@CheckData(name = "BadPacketsL", description = "Sent impossible dig packet")
public class BadPacketsL extends Check implements PacketCheck {

    public BadPacketsL(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            final WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);

            if (packet.getAction() == DiggingAction.START_DIGGING || packet.getAction() == DiggingAction.FINISHED_DIGGING || packet.getAction() == DiggingAction.CANCELLED_DIGGING)
                return;

            final boolean directionChange = packet.getAction() == DiggingAction.CHANGE_DESTROY_DIRECTION;
            // 1.8 and above clients always send digging packets that aren't used for digging at 0, 0, 0, facing DOWN
            // 1.7 and below clients do the same, except use SOUTH for RELEASE_USE_ITEM
            final boolean allowLegacyFace = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_7_10)
                    && packet.getAction() == DiggingAction.RELEASE_USE_ITEM;
            final boolean isValidFace = directionChange ? packet.getBlockFaceId() >= 0 && packet.getBlockFaceId() <= 5
                    : packet.getBlockFaceId() == 0 || allowLegacyFace && packet.getBlockFaceId() == 255;

            if (!isValidFace
                    || !directionChange && (packet.getBlockPosition().getX() != 0
                    || packet.getBlockPosition().getY() != 0
                    || packet.getBlockPosition().getZ() != 0)
                    || packet.getSequence() != 0
            ) {
                if (flagAndAlert("pos="
                        + packet.getBlockPosition().getX() + ", " + packet.getBlockPosition().getY() + ", " + packet.getBlockPosition().getZ()
                        + ", face=" + packet.getBlockFace()
                        + ", sequence=" + packet.getSequence()
                        + ", action=" + packet.getAction().toString().toLowerCase(Locale.ROOT)
                ) && shouldModifyPackets() && canCancel(packet.getAction())) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
