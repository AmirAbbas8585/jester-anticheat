package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "BadPacketsT")
public class BadPacketsT extends Check implements PacketCheck {
    private final double maxHorizontalDisplacement;
    private final double minVerticalDisplacement;
    private final double maxVerticalDisplacement;

    public BadPacketsT(final GrimPlayer player) {
        super(player);
        double expansion = player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.1 : 0;
        maxHorizontalDisplacement = 0.3001 + expansion;
        minVerticalDisplacement = -0.0001 - expansion;
        maxVerticalDisplacement = 2.0001 + expansion;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (player.compensatedEntities.self.getRiding() != null) return;

        if (event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) {
            final WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            wrapper.getTarget().ifPresent(targetVector -> {
                final PacketEntity packetEntity = player.compensatedEntities.getEntity(wrapper.getEntityId());
                if (packetEntity == null) {
                    return;
                }

                if (!EntityTypes.PLAYER.equals(packetEntity.type)) {
                    return;
                }

                final float scale = (float) packetEntity.getAttributeValue(Attributes.SCALE);
                if (targetVector.y > (minVerticalDisplacement * scale) && targetVector.y < (maxVerticalDisplacement * scale)
                        && Math.abs(targetVector.x) < (maxHorizontalDisplacement * scale)
                        && Math.abs(targetVector.z) < (maxHorizontalDisplacement * scale)) {
                    return;
                }

                final String verbose = String.format("%.5f/%.5f/%.5f",
                        targetVector.x, targetVector.y, targetVector.z);
                flagAndAlert(verbose);
            });
        }
    }
}
