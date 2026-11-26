package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectateEntity;

@CheckData(name = "BadPacketsC", description = "Interacted with self")
public class BadPacketsC extends Check implements PacketCheck {
    public BadPacketsC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.cameraEntity.isSelf()) return;

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            onInteract(event, new WrapperPlayClientInteractEntity(event).getEntityId());
        } else if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            onInteract(event, new WrapperPlayClientAttack(event).getEntityId());
        } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY) {
            WrapperPlayClientSpectateEntity packet = new WrapperPlayClientSpectateEntity(event);
            if (!packet.hasEntityId()) return;
            onInteract(event, packet.getEntityId());
        }
    }

    private void onInteract(PacketReceiveEvent event, int entityId) {
        if (entityId == player.entityID && flagAndAlert() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
