package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUseBed;

/**
 * Tracks the player's in-bed state from server-sent packets.
 *
 * USE_BED     → player enters bed (isInBed = true, bedPosition updated)
 * ENTITY_ANIMATION WAKE_UP → player exits bed (isInBed = false)
 *
 * This listener is separated from PacketSelfMetadataListener so the
 * bed-state logic is self-contained and easy to reason about.
 */
public class BedStateTracker extends PacketListenerAbstract {

    public BedStateTracker() {
        super(PacketListenerPriority.HIGH);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.USE_BED) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerUseBed bed = new WrapperPlayServerUseBed(event);
            if (player.entityID != bed.getEntityId()) return;

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                player.isInBed = true;
                player.bedPosition = new Vector3d(
                        bed.getPosition().getX() + 0.5,
                        bed.getPosition().getY(),
                        bed.getPosition().getZ() + 0.5);
            });
        }

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_ANIMATION) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerEntityAnimation animation = new WrapperPlayServerEntityAnimation(event);
            if (player.entityID != animation.getEntityId()) return;
            if (animation.getType() != WrapperPlayServerEntityAnimation.EntityAnimationType.WAKE_UP) return;

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.isInBed = false);
            event.getTasksAfterSend().add(player::sendTransaction);
        }
    }
}
