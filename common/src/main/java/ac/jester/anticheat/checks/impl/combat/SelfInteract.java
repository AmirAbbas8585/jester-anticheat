package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * SelfInteract — detects a player attacking or interacting with their own entity ID.
 *
 * In vanilla Minecraft it is impossible to target your own entity in any INTERACT_ENTITY
 * packet because the server never sends the player's own entity to themselves.
 * Any client sending INTERACT_ENTITY with the player's own entityID is using a hack.
 */
@CheckData(name = "SelfInteract", description = "Interacted with own entity")
public final class SelfInteract extends Check implements PacketCheck {

    public SelfInteract(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getEntityId() != player.entityID) return;

        if (flagAndAlert("entityId=" + interact.getEntityId() + " action=" + interact.getAction())
                && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
