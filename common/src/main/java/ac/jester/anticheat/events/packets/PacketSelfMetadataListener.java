package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.nmsutil.WatchableIndexUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import java.util.List;
import java.util.Optional;

public class PacketSelfMetadataListener extends PacketListenerAbstract {
    public PacketSelfMetadataListener() {
        super(PacketListenerPriority.HIGH);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata entityMetadata = new WrapperPlayServerEntityMetadata(event);

            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null)
                return;

            if (entityMetadata.getEntityId() == player.entityID) {
                boolean hasSendTransaction = false;

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14)) {
                    List<EntityData<?>> metadataStuff = entityMetadata.getEntityMetadata();

                    metadataStuff.removeIf(element -> element.getIndex() == 6);
                    entityMetadata.setEntityMetadata(metadataStuff);
                    event.markForReEncode(true);
                }

                EntityData<?> watchable = WatchableIndexUtil.getIndex(entityMetadata.getEntityMetadata(), 0);

                if (watchable != null) {
                    Object zeroBitField = watchable.getValue();

                    if (zeroBitField instanceof Byte) {
                        byte field = (byte) zeroBitField;
                        boolean isGliding = (field & 0x80) == 0x80 && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9);
                        boolean isSwimming = (field & 0x10) == 0x10;
                        boolean isSprinting = (field & 0x8) == 0x8;

                        if (!hasSendTransaction) player.sendTransaction();
                        hasSendTransaction = true;

                        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                            player.isSwimming = isSwimming;
                            player.lastSprinting = isSprinting;
                            if (player.isGliding != isGliding) {
                                player.pointThreeEstimator.updatePlayerGliding();
                            }
                            player.isGliding = isGliding;
                        });
                    }
                }

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
                    EntityData<?> gravity = WatchableIndexUtil.getIndex(entityMetadata.getEntityMetadata(), 5);

                    if (gravity != null) {
                        Object gravityObject = gravity.getValue();

                        if (gravityObject instanceof Boolean) {
                            if (!hasSendTransaction) player.sendTransaction();
                            hasSendTransaction = true;

                            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                                player.playerEntityHasGravity = !((Boolean) gravityObject);
                            });
                        }
                    }
                }

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_17)) {
                    EntityData<?> frozen = WatchableIndexUtil.getIndex(entityMetadata.getEntityMetadata(), 7);

                    if (frozen != null) {
                        if (!hasSendTransaction) player.sendTransaction();
                        hasSendTransaction = true;
                        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(),
                                () -> player.powderSnowFrozenTicks = (int) frozen.getValue());
                    }
                }

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14)) {
                    int id;

                    if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_14_4)) {
                        id = 12;
                    } else if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_16_5)) {
                        id = 13;
                    } else {
                        id = 14;
                    }

                    EntityData<?> bedObject = WatchableIndexUtil.getIndex(entityMetadata.getEntityMetadata(), id);
                    if (bedObject != null) {
                        if (!hasSendTransaction) player.sendTransaction();
                        hasSendTransaction = true;

                        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                            Optional<Vector3i> bed = (Optional<Vector3i>) bedObject.getValue();
                            if (bed.isPresent()) {
                                player.isInBed = true;
                                Vector3i bedPos = bed.get();
                                player.bedPosition = new Vector3d(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5);
                            } else {
                                player.isInBed = false;
                            }
                        });
                    }
                }

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13) &&
                        player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
                    EntityData<?> riptide = WatchableIndexUtil.getIndex(entityMetadata.getEntityMetadata(), PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_17) ? 8 : 7);

                    if (riptide != null && riptide.getValue() instanceof Byte) {
                        boolean isRiptiding = (((byte) riptide.getValue()) & 0x04) == 0x04;

                        if (!hasSendTransaction) player.sendTransaction();
                        hasSendTransaction = true;

                        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(),
                                () -> player.isRiptidePose = isRiptiding);

                        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
                            boolean isActive = (((byte) riptide.getValue()) & 1) > 0;
                            boolean isOffhand = (((byte) riptide.getValue()) & 2) > 0;

                            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(),
                                    () -> player.packetStateData.setSlowedByUsingItem(false));

                            int markedTransaction = player.lastTransactionSent.get();

                            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> {
                                if (player.packetStateData.slowedByUsingItemTransaction < markedTransaction) {
                                    PacketPlayerDigging.handleUseItem(player, isOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                                    player.packetStateData.setSlowedByUsingItem(isActive);

                                    if (isActive) {
                                        player.packetStateData.itemInUseHand = isOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                                    }
                                }
                            });

                            event.getTasksAfterSend().add(player::sendTransaction);
                        }
                    }
                }
            }
        }
    }
}
