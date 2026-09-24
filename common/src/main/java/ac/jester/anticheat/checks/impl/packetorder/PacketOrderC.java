package ac.jester.anticheat.checks.impl.packetorder;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "PacketOrderC", description = "Sent INTERACT and INTERACT_AT entity packets in the wrong order")
public class PacketOrderC extends Check implements PacketCheck {
    private final boolean exempt = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_7_10)
            || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_1)
            || PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_26_1);
    private boolean sentInteractAt = false;
    private int requiredEntity;
    private InteractionHand requiredHand;
    private boolean requiredSneaking;

    public PacketOrderC(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (exempt) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            final WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);

            final PacketEntity entity = player.compensatedEntities.entityMap.get(packet.getEntityId());

            if (entity != null && entity.type == EntityTypes.ARMOR_STAND) return;

            final boolean sneaking = packet.isSneaking().orElse(false);

            switch (packet.getAction()) {
                case INTERACT:
                    if (!sentInteractAt) {
                        if (flagAndAlert("Skipped Interact-At") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else if (packet.getEntityId() != requiredEntity || packet.getHand() != requiredHand || sneaking != requiredSneaking) {
                        String verbose = "requiredEntity=" + requiredEntity + ", entity=" + packet.getEntityId()
                                + ", requiredHand=" + requiredHand + ", hand=" + packet.getHand()
                                + ", requiredSneaking=" + requiredSneaking + ", sneaking=" + sneaking;
                        if (flagAndAlert(verbose) && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    }

                    sentInteractAt = false;
                    break;
                case INTERACT_AT:
                    if (sentInteractAt) {
                        if (flagAndAlert("Skipped Interact") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    }

                    requiredHand = packet.getHand();
                    requiredEntity = packet.getEntityId();
                    requiredSneaking = sneaking;
                    sentInteractAt = true;
                    break;
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (sentInteractAt) {
                sentInteractAt = false;
                flagAndAlert("Skipped Interact (Tick)");
            }
        }
    }
}
