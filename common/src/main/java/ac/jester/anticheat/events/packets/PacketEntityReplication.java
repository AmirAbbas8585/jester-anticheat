package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import ac.jester.anticheat.utils.data.SprintingState;
import ac.jester.anticheat.utils.data.TrackerData;
import ac.jester.anticheat.utils.data.interpolation.EntityMovementTransaction;
import ac.jester.anticheat.utils.data.packetentity.DashableEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityHook;
import ac.jester.anticheat.utils.enums.Pose;
import ac.jester.anticheat.utils.nmsutil.EntityMetadataPoseUtil;
import ac.jester.anticheat.utils.viaversion.ViaMovementTranslator;
import ac.jester.anticheat.utils.viaversion.ViaVersionUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.vector.positionpath.LinearPositionPath;
import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.LinearVecDelta;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.VecDelta;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPainting;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketEntityReplication extends Check implements PacketCheck {
    private final AtomicBoolean hasSentPreWavePacket = new AtomicBoolean(true);

    private final List<Integer> despawnedEntitiesThisTransaction = new ArrayList<>();

    private int maxFireworkBoostPing = 1000;

    public PacketEntityReplication(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isTickPacket(event.getPacketType())) return;
        player.compensatedEntities.entitiesRemovedThisTick.clear();
        boolean isTickingReliably = player.isTickingReliablyFor(3);

        PacketEntity playerVehicle = player.compensatedEntities.self.getRiding();
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity == playerVehicle && !player.vehicleData.lastDummy) {
                entity.setPositionRaw(player, entity.getPossibleLocationBoxes());
            } else {
                entity.onMovement(isTickingReliably);
            }
        }

        if (player.vehicleData.camelSprintingState == SprintingState.STOPPING) {
            player.vehicleData.camelSprintingState = SprintingState.STOPPED;
        } else if (player.vehicleData.camelSprintingState == SprintingState.STOPPED && player.isSprinting) {
            player.vehicleData.camelSprintingState = SprintingState.STARTED;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if ((event.getPacketType() == PacketType.Play.Server.PING || event.getPacketType() == PacketType.Play.Server.WINDOW_CONFIRMATION) && player.packetStateData.lastServerTransWasValid) {
            despawnedEntitiesThisTransaction.clear();
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity packetOutEntity = new WrapperPlayServerSpawnLivingEntity(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getEntityUUID(), packetOutEntity.getEntityType(), packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), packetOutEntity.getEntityMetadata(), 0);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packetOutEntity = new WrapperPlayServerSpawnEntity(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID().orElse(null), packetOutEntity.getEntityType(), packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), null, packetOutEntity.getData());
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer packetOutEntity = new WrapperPlayServerSpawnPlayer(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID(), EntityTypes.PLAYER, packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), packetOutEntity.getEntityMetadata(), 0);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PAINTING) {
            WrapperPlayServerSpawnPainting packetOutEntity = new WrapperPlayServerSpawnPainting(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID(), EntityTypes.PAINTING, packetOutEntity.getPosition().toVector3d(), 0, 0f, null, packetOutEntity.getDirection().getHorizontalIndex());
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove move = new WrapperPlayServerEntityRelativeMove(event);
            double deltaX = move.getDeltaX();
            double deltaY = move.getDeltaY();
            double deltaZ = move.getDeltaZ();
            VecDelta delta = null;

            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3)) {
                delta = ViaMovementTranslator.convert(player.getClientVersion(), move.getServerVersion(), move.getDelta());
            } else if (move.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_3)) {
                LinearVecDelta legacyDelta = ViaMovementTranslator.modernToLegacy(move.getDelta());
                deltaX = legacyDelta.dx();
                deltaY = legacyDelta.dy();
                deltaZ = legacyDelta.dz();
            }

            handleMoveEntity(event, move.getEntityId(), deltaX, deltaY, deltaZ, null, null, true, true, delta, null);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation move = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            double deltaX = move.getDeltaX();
            double deltaY = move.getDeltaY();
            double deltaZ = move.getDeltaZ();
            VecDelta delta = null;

            float rotationScale = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3) ? 1F : 0.7111111F;
            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3)) {
                delta = ViaMovementTranslator.convert(player.getClientVersion(), move.getServerVersion(), move.getDelta());
            } else if (move.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_3)) {
                LinearVecDelta legacyDelta = ViaMovementTranslator.modernToLegacy(move.getDelta());
                deltaX = legacyDelta.dx();
                deltaY = legacyDelta.dy();
                deltaZ = legacyDelta.dz();
            }

            handleMoveEntity(event, move.getEntityId(), deltaX, deltaY, deltaZ, move.getYaw() * rotationScale, move.getPitch() * rotationScale, true, true, delta, null);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport move = new WrapperPlayServerEntityTeleport(event);
            Vector3d pos = move.getPosition();
            LinearPositionPath path = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3) ? new LinearPositionPath(pos) : null;
            handleMoveEntity(event, move.getEntityId(), pos.getX(), pos.getY(), pos.getZ(), move.getYaw(), move.getPitch(), false, true, null, path);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync move = new WrapperPlayServerEntityPositionSync(event);
            final Vector3d pos;
            PositionPath path = null;
            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3)) {
                path = ViaMovementTranslator.convert(player.getClientVersion(), move.getServerVersion(), move.getPosition());
                pos = path.getEndPosition();
            } else if (move.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_3)) {
                pos = ViaMovementTranslator.modernToLegacy(move.getPosition()).getEndPosition();
            } else {
                pos = move.getValues().getPosition();
            }

            handleMoveEntity(event, move.getId(), pos.getX(), pos.getY(), pos.getZ(), move.getYRot(), move.getXRot(), false, true, null, path);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation move = new WrapperPlayServerEntityRotation(event);
            float rotationScale = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3) ? 1F : 0.7111111F;
            handleMoveEntity(event, move.getEntityId(), 0, 0, 0, move.getYaw() * rotationScale, move.getPitch() * rotationScale, true, false, null, null);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata entityMetadata = new WrapperPlayServerEntityMetadata(event);
            schedulePoseTransition(entityMetadata, event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.compensatedEntities.updateEntityMetadata(entityMetadata.getEntityId(), entityMetadata.getEntityMetadata()));
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment equipment = new WrapperPlayServerEntityEquipment(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.compensatedEntities.updateEntityEquipment(equipment.getEntityId(), equipment.getEquipment()));
        }

        else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate info = new WrapperPlayServerPlayerInfoUpdate(event);
            if (info.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) {
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : info.getEntries()) {
                        final UserProfile gameProfile = entry.getGameProfile();
                        final UUID uuid = gameProfile.getUUID();
                        player.compensatedEntities.profiles.put(uuid, gameProfile);
                    }
                });
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            WrapperPlayServerPlayerInfoRemove remove = new WrapperPlayServerPlayerInfoRemove(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> remove.getProfileIds().forEach(player.compensatedEntities.profiles::remove));
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
            WrapperPlayServerPlayerInfo info = new WrapperPlayServerPlayerInfo(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (info.getAction() == WrapperPlayServerPlayerInfo.Action.ADD_PLAYER) {
                    for (WrapperPlayServerPlayerInfo.PlayerData entry : info.getPlayerDataList()) {
                        final UserProfile gameProfile = entry.getUserProfile();
                        final UUID uuid = gameProfile.getUUID();
                        player.compensatedEntities.profiles.put(uuid, gameProfile);
                    }
                } else if (info.getAction() == WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER) {
                    info.getPlayerDataList().forEach(profile -> player.compensatedEntities.profiles.remove(profile.getUserProfile().getUUID()));
                }
            });
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT) {
            WrapperPlayServerEntityEffect effect = new WrapperPlayServerEntityEffect(event);

            PotionType type = effect.getPotionType();

            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9) && ViaVersionUtil.isAvailable && type.getId(player.getClientVersion()) > 23) {
                event.setCancelled(true);
                return;
            }

            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_13) && ViaVersionUtil.isAvailable && type.getId(player.getClientVersion()) == 30) {
                event.setCancelled(true);
                return;
            }

            if (isDirectlyAffectingPlayer(player, effect.getEntityId())) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                PacketEntity entity = player.compensatedEntities.getEntity(effect.getEntityId());
                if (entity == null) return;

                entity.addPotionEffect(type, effect.getEffectAmplifier());
            });
        } else if (event.getPacketType() == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            WrapperPlayServerRemoveEntityEffect effect = new WrapperPlayServerRemoveEntityEffect(event);

            if (isDirectlyAffectingPlayer(player, effect.getEntityId())) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                PacketEntity entity = player.compensatedEntities.getEntity(effect.getEntityId());
                if (entity == null) return;

                entity.removePotionEffect(effect.getPotionType());
            });
        } else if (event.getPacketType() == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            WrapperPlayServerUpdateAttributes attributes = new WrapperPlayServerUpdateAttributes(event);

            int entityID = attributes.getEntityId();

            if (isDirectlyAffectingPlayer(player, entityID)) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(),
                    () -> player.compensatedEntities.updateAttributes(entityID, attributes.getProperties()));
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            if (status.getStatus() == 3) {
                PacketEntity entity = player.compensatedEntities.getEntity(status.getEntityId());

                if (entity == null) return;
                entity.isDead = true;
            }

            if (status.getStatus() == 9) {
                if (status.getEntityId() != player.entityID) return;

                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
            }

            if (status.getStatus() == 31) {
                PacketEntity hook = player.compensatedEntities.getEntity(status.getEntityId());
                if (!(hook instanceof PacketEntityHook hookEntity)) return;

                if (hookEntity.attached == player.entityID) {
                    player.sendTransaction();
                    player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.uncertaintyHandler.fishingRodPulls.add(hookEntity.owner));
                }
            }

            if (status.getStatus() >= 24 && status.getStatus() <= 28 && status.getEntityId() == player.entityID) {
                player.compensatedEntities.self.opLevel = status.getStatus() - 24;
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);

            if (slot.getWindowId() == 0) {
                Runnable task = () -> {
                    if (slot.getSlot() - 36 == player.packetStateData.lastSlotSelected && (
                            !player.inventory.getHeldItem().is(slot.getItem().getType()) || player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)
                    ) || slot.getSlot() == 45 && !player.inventory.getOffHand().is(slot.getItem().getType())) {
                        InteractionHand hand = slot.getSlot() == 45 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                        if (hand == player.packetStateData.itemInUseHand) {
                            player.packetStateData.setSlowedByUsingItem(false);
                        }

                        if (player.isResetItemUsageOnItemUpdate() && hand == GrimAPI.INSTANCE.getItemResetHandler().getItemUsageHand(player.platformPlayer)) {
                            GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                        }
                    }
                };

                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), task);
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, task);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems items = new WrapperPlayServerWindowItems(event);

            if (items.getWindowId() == 0) {
                Runnable task = () -> {
                    if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) {
                        player.packetStateData.setSlowedByUsingItem(false);
                        if (player.isResetItemUsageOnItemUpdate()) {
                            GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                        }
                    } else {
                        if (items.getItems().size() > 45 && !player.inventory.getOffHand().is(items.getItems().get(45).getType())) {
                            if (player.packetStateData.itemInUseHand == InteractionHand.OFF_HAND) {
                                player.packetStateData.setSlowedByUsingItem(false);
                            }

                            if (player.isResetItemUsageOnItemUpdate() && GrimAPI.INSTANCE.getItemResetHandler().getItemUsageHand(player.platformPlayer) == InteractionHand.OFF_HAND) {
                                GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                            }
                        }

                        if (!player.inventory.getHeldItem().is(items.getItems().get(player.packetStateData.lastSlotSelected + 36).getType())) {
                            if (player.packetStateData.itemInUseHand == InteractionHand.MAIN_HAND) {
                                player.packetStateData.setSlowedByUsingItem(false);
                            }

                            if (player.isResetItemUsageOnItemUpdate() && GrimAPI.INSTANCE.getItemResetHandler().getItemUsageHand(player.platformPlayer) == InteractionHand.MAIN_HAND) {
                                GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                            }
                        }
                    }
                };

                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), task);
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, task);
            }
        }

        else if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
        } else if (event.getPacketType() == PacketType.Play.Server.OPEN_HORSE_WINDOW) {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            WrapperPlayServerSetPassengers mount = new WrapperPlayServerSetPassengers(event);

            int vehicleID = mount.getEntityId();
            int[] passengers = mount.getPassengers();

            handleMountVehicle(event, vehicleID, passengers);
        } else if (event.getPacketType() == PacketType.Play.Server.ATTACH_ENTITY) {
            WrapperPlayServerAttachEntity attach = new WrapperPlayServerAttachEntity(event);

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9))
                return;

            if (!attach.isLeash()) {
                int vehicleID = attach.getHoldingId();
                int attachID = attach.getAttachedId();
                TrackerData trackerData = player.compensatedEntities.getTrackedEntity(attachID);

                if (trackerData != null) {
                    if (vehicleID == -1) {
                        vehicleID = trackerData.getLegacyPointEightMountedUpon();
                        handleMountVehicle(event, vehicleID, new int[]{});
                    } else {
                        trackerData.setLegacyPointEightMountedUpon(vehicleID);
                        handleMountVehicle(event, vehicleID, new int[]{attachID});
                    }
                } else {
                    LogUtil.warn("Server sent an invalid attach entity packet for entity " + attach.getHoldingId() + " with passenger " + attach.getAttachedId() + "! The client ignores this.");
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(event);

            int[] destroyEntityIds = destroy.getEntityIds();

            for (int entityID : destroyEntityIds) {
                despawnedEntitiesThisTransaction.add(entityID);
                player.compensatedEntities.serverPositionsMap.remove(entityID);
                if (player.compensatedEntities.serverPlayerVehicle != null && player.compensatedEntities.serverPlayerVehicle == entityID) {
                    player.compensatedEntities.serverPlayerVehicle = null;
                }
            }

            final int destroyTransaction = player.lastTransactionSent.get() + 1;
            player.latencyUtils.addRealTimeTask(destroyTransaction, () -> {
                for (int entityId : destroyEntityIds) {
                    player.compensatedEntities.removeEntity(entityId);
                    player.dashableEntities.removeEntity(entityId);
                    player.fireworks.removeFirework(entityId);
                    player.compensatedEntities.entitiesRemovedThisTick.add(entityId);
                }
            });

            if (maxFireworkBoostPing > 0) {
                player.runNettyTaskInMs(() -> {
                    if (player.lastTransactionReceived.get() >= destroyTransaction) return;
                    for (int entityID : destroyEntityIds) {
                        if (player.fireworks.hasFirework(entityID)) {
                            player.getSetbackTeleportUtil().executeViolationSetback();
                            break;
                        }
                    }
                }, maxFireworkBoostPing);
            }
        }
    }

    private void handleMountVehicle(PacketSendEvent event, int vehicleID, int[] passengers) {
        boolean wasInVehicle = player.compensatedEntities.serverPlayerVehicle != null && player.compensatedEntities.serverPlayerVehicle == vehicleID;
        boolean inThisVehicle = false;

        for (int passenger : passengers) {
            inThisVehicle = passenger == player.entityID;
            if (inThisVehicle) break;
        }

        if (inThisVehicle && !wasInVehicle) {
            player.handleMountVehicle(vehicleID);
        }

        if (!inThisVehicle && wasInVehicle) {
            player.handleDismountVehicle(event);
        }
        if (wasInVehicle || inThisVehicle) {
            player.sendTransaction();
        }
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            PacketEntity vehicle = player.compensatedEntities.getEntity(vehicleID);

            if (vehicle == null) return;

            for (PacketEntity passenger : new ArrayList<>(vehicle.passengers)) {
                passenger.eject();
            }

            for (int entityID : passengers) {
                PacketEntity passenger = player.compensatedEntities.getEntity(entityID);
                if (passenger == null) continue;
                passenger.mount(vehicle);
            }
        });
    }

    private void handleMoveEntity(PacketSendEvent event, int entityId, double deltaX, double deltaY, double deltaZ, Float yaw, Float pitch, boolean isRelative, boolean hasPos, @Nullable VecDelta delta, @Nullable PositionPath path) {
        TrackerData data = player.compensatedEntities.getTrackedEntity(entityId);
        if (data == null) return;

        final boolean didNotSendPreWave = hasSentPreWavePacket.compareAndSet(false, true);
        if (didNotSendPreWave) player.sendTransaction();

        if (delta != null) {
            path = delta.applyAsPath(new Vector3d(data.getX(), data.getY(), data.getZ()));
        }

        if (isRelative) {
            boolean vanillaVehicleFlight = player.compensatedEntities.serverPlayerVehicle != null
                    && player.compensatedEntities.serverPlayerVehicle == entityId
                    && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)
                    && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_2)
                    && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9);

            if (vanillaVehicleFlight ||
                    ((Math.abs(deltaX) >= 3.9375 || Math.abs(deltaY) >= 3.9375 || Math.abs(deltaZ) >= 3.9375) && player.getClientVersion().isOlderThan(ClientVersion.V_1_9) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9))) {
                Vector3d targetPosition = path != null ? path.getEndPosition() : new Vector3d(data.getX() + deltaX, data.getY() + deltaY, data.getZ() + deltaZ);
                player.user.writePacket(new WrapperPlayServerEntityTeleport(entityId, targetPosition, yaw == null ? data.getXRot() : yaw, pitch == null ? data.getYRot() : pitch, false));
                event.setCancelled(true);
                return;
            }
        }

        if (path != null) {
            Vector3d endPosition = path.getEndPosition();
            data.setX(endPosition.x);
            data.setY(endPosition.y);
            data.setZ(endPosition.z);
        } else if (isRelative) {
            data.setX(data.getX() + deltaX);
            data.setY(data.getY() + deltaY);
            data.setZ(data.getZ() + deltaZ);
        } else {
            data.setX(deltaX);
            data.setY(deltaY);
            data.setZ(deltaZ);
        }
        if (yaw != null) {
            data.setXRot(yaw);
            data.setYRot(pitch);
        }

        if (data.getLastTransactionHung() == player.lastTransactionSent.get()) {
            player.sendTransaction();
        }
        data.setLastTransactionHung(player.lastTransactionSent.get());

        int lastTrans = player.lastTransactionSent.get();

        boolean positionSync = event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC;
        EntityMovementTransaction task = new EntityMovementTransaction(player, entityId, path, yaw, pitch, isRelative, hasPos, deltaX, deltaY, deltaZ, positionSync, lastTrans);
        player.latencyUtils.addRealTimeTask(lastTrans, task);
        player.latencyUtils.addRealTimeTask(lastTrans + 1, task);
    }

    public void addEntity(int entityID, UUID uuid, EntityType type, Vector3d position, float xRot, float yRot, List<EntityData<?>> entityMetadata, int extraData) {
        if (despawnedEntitiesThisTransaction.contains(entityID)) {
            player.sendTransaction();
        }

        player.compensatedEntities.serverPositionsMap.put(entityID, new TrackerData(position.getX(), position.getY(), position.getZ(), xRot, yRot, type, player.lastTransactionSent.get()));

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            PacketEntity entity = player.compensatedEntities.addEntity(entityID, uuid, type, position, xRot, extraData);
            if (entity != null) {
                entity.initializeInterpolationRotation(xRot, yRot);
                entity.onSecondTransaction(player.lastTransactionReceived.get());
            }
            if (entity instanceof DashableEntity dashable) {
                player.dashableEntities.addEntity(entityID, dashable);
            }

            if (entityMetadata != null) {
                if (EntityMetadataPoseUtil.usesPoseMetadata(entity) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)) {
                    Pose initialPose = EntityMetadataPoseUtil.getPoseFromMetadata(entityMetadata);
                    if (initialPose != null) {
                        entity.currentPose = initialPose;
                    }
                }

                player.compensatedEntities.updateEntityMetadata(entityID, entityMetadata);
            }
        });
    }

    private void schedulePoseTransition(WrapperPlayServerEntityMetadata entityMetadata, PacketSendEvent event) {
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14)) return;

        int entityId = entityMetadata.getEntityId();
        if (entityId == player.entityID) return;

        Pose newPose = EntityMetadataPoseUtil.getPoseFromMetadata(entityMetadata.getEntityMetadata());
        if (newPose == null) return;

        boolean shouldTrackPoseTransition = false;
        PacketEntity entity = player.compensatedEntities.getEntity(entityId);
        if (entity != null) {
            shouldTrackPoseTransition = EntityMetadataPoseUtil.usesPoseMetadata(entity);
        } else {
            TrackerData trackedEntity = player.compensatedEntities.getTrackedEntity(entityId);
            if (trackedEntity != null) {
                shouldTrackPoseTransition = EntityMetadataPoseUtil.usesPoseMetadata(trackedEntity.getEntityType());
            }
        }

        if (!shouldTrackPoseTransition) return;

        player.sendTransaction();
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            PacketEntity target = player.compensatedEntities.getEntity(entityId);
            if (target == null) {
                return;
            }

            target.beginPoseTransition(newPose);
        });

        event.getTasksAfterSend().add(() -> {
            player.sendTransaction();
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                PacketEntity target = player.compensatedEntities.getEntity(entityId);
                if (target == null) {
                    return;
                }

                target.completePoseTransition(newPose);
            });
        });
    }

    private boolean isDirectlyAffectingPlayer(GrimPlayer player, int entityID) {
        return (player.compensatedEntities.serverPlayerVehicle == null && entityID == player.entityID) ||
                (player.compensatedEntities.serverPlayerVehicle != null && entityID == player.compensatedEntities.serverPlayerVehicle);
    }

    public void onEndOfTickEvent() {
        player.sendTransaction(true);
    }

    public void tickStartTick() {
        hasSentPreWavePacket.set(false);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxFireworkBoostPing = config.getIntElse("max-ping-firework-boost", 1000);
    }
}
