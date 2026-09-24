package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.*;
import ac.jester.anticheat.utils.blockplace.BlockPlaceResult;
import ac.jester.anticheat.utils.blockplace.ConsumesBlockPlace;
import ac.jester.anticheat.utils.change.BlockModification;
import ac.jester.anticheat.utils.data.*;
import ac.jester.anticheat.utils.inventory.Inventory;
import ac.jester.anticheat.utils.latency.CompensatedWorld;
import ac.jester.anticheat.utils.math.VectorUtils;
import ac.jester.anticheat.utils.nmsutil.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class CheckManagerListener extends PacketListenerAbstract {
    private static final Function<StateType, Boolean> BREAKABLE = type -> !type.isAir() && type.getHardness() != -1.0f && type != StateTypes.WATER && type != StateTypes.LAVA;

    public CheckManagerListener() {
        super(PacketListenerPriority.LOW);
    }

    private static void placeWaterLavaSnowBucket(GrimPlayer player, ItemStack held, StateType toPlace, InteractionHand hand, int sequence) {
        HitData data = WorldRayTrace.getNearestBlockHitResult(player, StateTypes.AIR, false, true, true);
        if (data != null) {
            BlockPlace blockPlace = new BlockPlace(player, hand, data.position(), data.closestDirection().getFaceValue(), data.closestDirection(), held, data, sequence);

            boolean didPlace = false;

            if (Materials.isPlaceableWaterBucket(blockPlace.itemStack.getType()) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                blockPlace.replaceClicked = true;
                WrappedBlockState existing = blockPlace.getExistingBlockData();
                if (existing.hasProperty(StateValue.WATERLOGGED) && !existing.isWaterlogged()) {
                    didPlace = true;
                }
            }

            if (!didPlace) {
                blockPlace.replaceClicked = false;
                blockPlace.set(toPlace);
            }

            if (player.gamemode != GameMode.CREATIVE) {
                player.inventory.markSlotAsResyncing(blockPlace);
                if (hand == InteractionHand.MAIN_HAND) {
                    player.inventory.inventory.setHeldItem(ItemStack.builder().type(ItemTypes.BUCKET).amount(1).build());
                } else {
                    player.inventory.inventory.setPlayerInventoryItem(Inventory.SLOT_OFFHAND, ItemStack.builder().type(ItemTypes.BUCKET).amount(1).build());
                }
            }
        }
    }

    public static void handleQueuedPlaces(GrimPlayer player, boolean hasLook, float pitch, float yaw, long now) {
        BlockPlaceSnapshot snapshot;
        while ((snapshot = player.placeUseItemPackets.poll()) != null) {
            double lastX = player.x;
            double lastY = player.y;
            double lastZ = player.z;

            player.x = player.packetStateData.lastClaimedPosition.getX();
            player.y = player.packetStateData.lastClaimedPosition.getY();
            player.z = player.packetStateData.lastClaimedPosition.getZ();

            boolean lastSneaking = player.isSneaking;
            player.isSneaking = snapshot.sneaking();

            if (player.inVehicle()) {
                Vector3d posFromVehicle = BoundingBoxSize.getRidingOffsetFromVehicle(player.compensatedEntities.self.getRiding(), player);
                player.x = posFromVehicle.getX();
                player.y = posFromVehicle.getY();
                player.z = posFromVehicle.getZ();
            }

            if ((now - player.lastBlockPlaceUseItem < 15 || player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) && hasLook) {
                player.yaw = yaw;
                player.pitch = pitch;
            }

            player.compensatedWorld.startPredicting();
            handleBlockPlaceOrUseItem(snapshot.wrapper(), player);
            player.compensatedWorld.stopPredicting(snapshot.wrapper());

            player.x = lastX;
            player.y = lastY;
            player.z = lastZ;
            player.isSneaking = lastSneaking;
        }
    }

    public static void handleQueuedBreaks(GrimPlayer player, boolean hasLook, float pitch, float yaw, long now) {
        BlockBreak blockBreak;
        while ((blockBreak = player.queuedBreaks.poll()) != null) {
            double lastX = player.x;
            double lastY = player.y;
            double lastZ = player.z;

            player.x = player.packetStateData.lastClaimedPosition.getX();
            player.y = player.packetStateData.lastClaimedPosition.getY();
            player.z = player.packetStateData.lastClaimedPosition.getZ();

            if (player.inVehicle()) {
                Vector3d posFromVehicle = BoundingBoxSize.getRidingOffsetFromVehicle(player.compensatedEntities.self.getRiding(), player);
                player.x = posFromVehicle.getX();
                player.y = posFromVehicle.getY();
                player.z = posFromVehicle.getZ();
            }

            if ((now - player.lastBlockBreak < 15 || player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) && hasLook) {
                player.yaw = yaw;
                player.pitch = pitch;
            }

            player.checkManager.onPostFlyingBlockBreak(blockBreak);

            player.x = lastX;
            player.y = lastY;
            player.z = lastZ;
        }
    }

    private static void handleUseItem(GrimPlayer player, ItemStack placedWith, InteractionHand hand, int sequence) {
        if (placedWith.getType() == ItemTypes.LILY_PAD) {
            placeLilypad(player, hand, sequence);
            return;
        }

        StateType toBucketMat = Materials.transformBucketMaterial(placedWith.getType());
        if (toBucketMat != null) {
            placeWaterLavaSnowBucket(player, placedWith, toBucketMat, hand, sequence);
        }

        if (placedWith.getType() == ItemTypes.BUCKET) {
            placeBucket(player, hand, sequence);
        }
    }

    private static void handleBlockPlaceOrUseItem(PacketWrapper<?> packet, GrimPlayer player) {
        if (packet instanceof WrapperPlayClientPlayerBlockPlacement place &&
                PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9)) {
            if (player.gamemode == GameMode.SPECTATOR || player.gamemode == GameMode.ADVENTURE)
                return;

            if (place.getFace() == BlockFace.OTHER) {
                ItemStack placedWith = player.inventory.getHeldItem();
                if (place.getHand() == InteractionHand.OFF_HAND) {
                    placedWith = player.inventory.getOffHand();
                }

                handleUseItem(player, placedWith, place.getHand(), place.getSequence());
                return;
            }
        }

        if (packet instanceof WrapperPlayClientUseItem place) {
            if (player.gamemode == GameMode.SPECTATOR || player.gamemode == GameMode.ADVENTURE)
                return;

            ItemStack placedWith = player.inventory.getHeldItem();
            if (place.getHand() == InteractionHand.OFF_HAND) {
                placedWith = player.inventory.getOffHand();
            }

            handleUseItem(player, placedWith, place.getHand(), place.getSequence());
        }

        if (packet instanceof WrapperPlayClientPlayerBlockPlacement place) {
            ItemStack placedWith = player.inventory.getHeldItem();
            ItemStack offhand = player.inventory.getOffHand();

            boolean onlyAir = placedWith.isEmpty() && offhand.isEmpty();

            if ((!player.isSneaking || onlyAir) && place.getHand() == InteractionHand.MAIN_HAND) {
                Vector3i blockPosition = place.getBlockPosition();
                BlockPlace blockPlace = new BlockPlace(player, place.getHand(), blockPosition, place.getFaceId(), place.getFace(), placedWith, WorldRayTrace.getNearestBlockHitResult(player, null, true, false, false), place.getSequence());

                StateType placedAgainst = blockPlace.getPlacedAgainstMaterial();
                if (player.getClientVersion().isOlderThan(ClientVersion.V_1_11) && (placedAgainst == StateTypes.IRON_TRAPDOOR
                        || placedAgainst == StateTypes.IRON_DOOR || BlockTags.FENCES.contains(placedAgainst))
                        || player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8) && BlockTags.CAULDRONS.contains(placedAgainst)
                        || Materials.isClientSideInteractable(placedAgainst)) {
                    player.checkManager.onPostFlyingBlockPlace(blockPlace);
                    Vector3i location = blockPlace.position;
                    player.compensatedWorld.tickOpenable(location.x, location.y, location.z);
                    return;
                }

                if (ConsumesBlockPlace.consumesPlace(player, player.compensatedWorld.getBlock(blockPlace.position), blockPlace)) {
                    player.checkManager.onPostFlyingBlockPlace(blockPlace);
                    return;
                }
            }
        }

        if (packet instanceof WrapperPlayClientPlayerBlockPlacement place) {
            if (player.gamemode == GameMode.SPECTATOR || player.gamemode == GameMode.ADVENTURE)
                return;

            Vector3i blockPosition = place.getBlockPosition();
            BlockFace face = place.getFace();
            ItemStack placedWith = player.inventory.getHeldItem();
            if (place.getHand() == InteractionHand.OFF_HAND) {
                placedWith = player.inventory.getOffHand();
            }

            BlockPlace blockPlace = new BlockPlace(player, place.getHand(), blockPosition, place.getFaceId(), face, placedWith, WorldRayTrace.getNearestBlockHitResult(player, null, true, false, false), place.getSequence());
            player.checkManager.onPostFlyingBlockPlace(blockPlace);

            blockPlace.isInside = place.getInsideBlock().orElse(false);

            if (placedWith.getType().getPlacedType() != null || placedWith.getType() == ItemTypes.FLINT_AND_STEEL || placedWith.getType() == ItemTypes.FIRE_CHARGE) {
                BlockPlaceResult.getMaterialData(placedWith.getType()).applyBlockPlaceToWorld(player, blockPlace);
            }
        }
    }

    private static void placeBucket(GrimPlayer player, InteractionHand hand, int sequence) {
        HitData data = WorldRayTrace.getNearestBlockHitResult(player, null, true, false, true);

        if (data != null) {
            BlockPlace blockPlace = new BlockPlace(player, hand, data.position(), data.closestDirection().getFaceValue(), data.closestDirection(), ItemStack.EMPTY, data, sequence);
            blockPlace.replaceClicked = true;

            boolean placed = false;
            ItemType type = null;

            if (data.state().getType() == StateTypes.POWDER_SNOW) {
                blockPlace.set(StateTypes.AIR);
                type = ItemTypes.POWDER_SNOW_BUCKET;
                placed = true;
            }

            if (data.state().getType() == StateTypes.LAVA) {
                blockPlace.set(StateTypes.AIR);
                type = ItemTypes.LAVA_BUCKET;
                placed = true;
            }

            if (!placed && !player.compensatedWorld.isWaterSourceBlock(data.position().getX(), data.position().getY(), data.position().getZ()))
                return;

            if (data.state().getType() == StateTypes.KELP || data.state().getType() == StateTypes.SEAGRASS || data.state().getType() == StateTypes.TALL_SEAGRASS) {
                return;
            }

            if (!placed) {
                type = ItemTypes.WATER_BUCKET;
            }

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                WrappedBlockState existing = blockPlace.getExistingBlockData();
                if (existing.hasProperty(StateValue.WATERLOGGED)) {
                    existing.setWaterlogged(false);
                    blockPlace.set(existing);
                    placed = true;
                }
            }

            if (!placed) {
                blockPlace.set(StateTypes.AIR);
            }

            if (player.gamemode != GameMode.CREATIVE) {
                player.inventory.markSlotAsResyncing(blockPlace);
                setPlayerItem(player, hand, type);
            }
        }
    }

    public static void setPlayerItem(GrimPlayer player, InteractionHand hand, ItemType type) {
        if (player.gamemode != GameMode.CREATIVE) {
            if (hand == InteractionHand.MAIN_HAND) {
                if (player.inventory.getHeldItem().getAmount() == 1) {
                    player.inventory.inventory.setHeldItem(ItemStack.builder().type(type).amount(1).build());
                } else {
                    player.inventory.inventory.add(ItemStack.builder().type(type).amount(1).build());
                    player.inventory.getHeldItem().setAmount(player.inventory.getHeldItem().getAmount() - 1);
                }
            } else {
                if (player.inventory.getOffHand().getAmount() == 1) {
                    player.inventory.inventory.setPlayerInventoryItem(Inventory.SLOT_OFFHAND, ItemStack.builder().type(type).amount(1).build());
                } else {
                    player.inventory.inventory.add(Inventory.SLOT_OFFHAND, ItemStack.builder().type(type).amount(1).build());
                    player.inventory.getOffHand().setAmount(player.inventory.getOffHand().getAmount() - 1);
                }
            }
        }
    }

    private static void placeLilypad(GrimPlayer player, InteractionHand hand, int sequence) {
        HitData data = WorldRayTrace.getNearestBlockHitResult(player, null, true, false, true);

        if (data != null) {
            if (player.compensatedWorld.getFluidLevelAt(data.position().getX(), data.position().getY() + 1, data.position().getZ()) > 0)
                return;

            BlockPlace blockPlace = new BlockPlace(player, hand, data.position(), data.closestDirection().getFaceValue(), data.closestDirection(), ItemStack.EMPTY, data, sequence);
            blockPlace.replaceClicked = false;

            if (player.compensatedWorld.getWaterFluidLevelAt(data.position().getX(), data.position().getY(), data.position().getZ()) > 0
                    || data.state().getType() == StateTypes.ICE || data.state().getType() == StateTypes.FROSTED_ICE) {
                Vector3i pos = data.position();
                pos = pos.add(0, 1, 0);

                blockPlace.set(pos, StateTypes.LILY_PAD.createBlockState(CompensatedWorld.blockVersion));

                if (player.gamemode != GameMode.CREATIVE) {
                    player.inventory.markSlotAsResyncing(blockPlace);
                    if (hand == InteractionHand.MAIN_HAND) {
                        player.inventory.inventory.getHeldItem().setAmount(player.inventory.inventory.getHeldItem().getAmount() - 1);
                    } else {
                        player.inventory.getOffHand().setAmount(player.inventory.getOffHand().getAmount() - 1);
                    }
                }
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        if (event.getConnectionState() != ConnectionState.PLAY) {
            if (event.getConnectionState() != ConnectionState.CONFIGURATION) return;
            player.checkManager.onPacketReceive(event);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            Vector3d position = move.getPosition();
            player.packetStateData.lastPacketWasTeleport = player.getSetbackTeleportUtil().checkVehicleTeleportQueue(position.getX(), position.getY(), position.getZ());
        }

        TeleportAcceptData teleportData = null;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            player.serverOpenedInventoryThisTick = false;

            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);

            Vector3d position = VectorUtils.clampVector(flying.getLocation().getPosition());
            teleportData = flying.hasPositionChanged() && flying.hasRotationChanged() ? player.getSetbackTeleportUtil().checkTeleportQueue(position.getX(), position.getY(), position.getZ(), flying.getLocation().getYaw(), flying.getLocation().getPitch()) : new TeleportAcceptData();
            player.packetStateData.lastPacketWasTeleport = teleportData.isTeleport();

            if (flying.hasRotationChanged() && !flying.hasPositionChanged() && !flying.isOnGround() && !flying.isHorizontalCollision()) {
                List<RotationData> rotations = new ArrayList<>();

                for (RotationData data : player.pendingRotations) {
                    rotations.add(data);
                    if (!data.isAccepted()) {
                        break;
                    }
                }

                Collections.reverse(rotations);

                for (RotationData data : rotations) {
                    if (data.getYaw() == flying.getLocation().getYaw() && data.getPitch() == flying.getLocation().getPitch() && data.getTransaction() == player.getLastTransactionReceived()) {
                        player.packetStateData.lastPacketWasTeleport = true;
                        data.accept();
                        break;
                    }
                }
            }

            player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = isMojangStupid(player, event, flying);
        }

        if (player.inVehicle() ? event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE : WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            int kbEntityId = player.inVehicle() ? player.getRidingVehicleId() : player.entityID;

            VelocityData calculatedFirstBreadKb = player.checkManager.getKnockbackHandler().calculateFirstBreadKnockback(kbEntityId, player.lastTransactionReceived.get());
            VelocityData calculatedRequireKb = player.checkManager.getKnockbackHandler().calculateRequiredKB(kbEntityId, player.lastTransactionReceived.get(), false);
            player.firstBreadKB = calculatedFirstBreadKb == null ? player.firstBreadKB : calculatedFirstBreadKb;
            player.likelyKB = calculatedRequireKb == null ? player.likelyKB : calculatedRequireKb;

            VelocityData calculateFirstBreadExplosion = player.checkManager.getExplosionHandler().getFirstBreadAddedExplosion(player.lastTransactionReceived.get());
            VelocityData calculateRequiredExplosion = player.checkManager.getExplosionHandler().getPossibleExplosions(player.lastTransactionReceived.get(), false);
            player.firstBreadExplosion = calculateFirstBreadExplosion == null ? player.firstBreadExplosion : calculateFirstBreadExplosion;
            player.likelyExplosions = calculateRequiredExplosion == null ? player.likelyExplosions : calculateRequiredExplosion;
        }

        player.checkManager.onPrePredictionReceivePacket(event);

        if (event.isCancelled() && (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) || event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE)) {
            player.packetStateData.cancelDuplicatePacket = false;
            return;
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            Location pos = flying.getLocation();
            boolean ignoreRotation = player.packetStateData.lastPacketWasOnePointSeventeenDuplicate && player.isIgnoreDuplicatePacketRotation();
            handleFlying(player, pos.getX(), pos.getY(), pos.getZ(), ignoreRotation ? 0 : pos.getYaw(), ignoreRotation ? 0 : pos.getPitch(), flying.hasPositionChanged(), flying.hasRotationChanged() && !ignoreRotation, flying.isOnGround(), teleportData, event);
        }

        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE && player.inVehicle()) {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            Vector3d position = move.getPosition();

            player.lastX = player.x;
            player.lastY = player.y;
            player.lastZ = player.z;

            Vector3d clamp = VectorUtils.clampVector(position);
            player.x = clamp.getX();
            player.y = clamp.getY();
            player.z = clamp.getZ();

            player.yaw = move.getYaw();
            player.pitch = move.getPitch();

            final VehiclePositionUpdate update = new VehiclePositionUpdate(clamp, position, move.getYaw(), move.getPitch(), move.isOnGround(), player.packetStateData.lastPacketWasTeleport);
            player.checkManager.onVehiclePositionUpdate(update);

            player.packetStateData.receivedSteerVehicle = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            handleDigging(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
            player.lastBlockPlaceUseItem = System.currentTimeMillis();

            ItemStack placedWith = player.inventory.getHeldItem();
            if (packet.getHand() == InteractionHand.OFF_HAND) {
                placedWith = player.inventory.getOffHand();
            }

            if (packet.getFace() == BlockFace.OTHER && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9)) {
                player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
            } else {
                BlockPlace blockPlace = new BlockPlace(player, packet.getHand(), packet.getBlockPosition(), packet.getFaceId(), packet.getFace(), placedWith, WorldRayTrace.getNearestBlockHitResult(player, null, true, false, false), packet.getSequence());
                blockPlace.cursor = packet.getCursorPosition();

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_11) && player.getClientVersion().isOlderThan(ClientVersion.V_1_11)) {
                    if (packet.getCursorPosition().getX() * 15 % 1 == 0 && packet.getCursorPosition().getY() * 15 % 1 == 0 && packet.getCursorPosition().getZ() * 15 % 1 == 0) {
                        int trueByteX = (int) (packet.getCursorPosition().getX() * 15);
                        int trueByteY = (int) (packet.getCursorPosition().getY() * 15);
                        int trueByteZ = (int) (packet.getCursorPosition().getZ() * 15);

                        blockPlace.cursor = new Vector3f(trueByteX / 16f, trueByteY / 16f, trueByteZ / 16f);
                    }
                }

                player.checkManager.onBlockPlace(blockPlace);

                if (event.isCancelled() || blockPlace.isCancelled() || player.getSetbackTeleportUtil().shouldBlockMovement()) {
                    if (!event.isCancelled()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }

                    Vector3i facePos = new Vector3i(packet.getBlockPosition().getX() + packet.getFace().getModX(), packet.getBlockPosition().getY() + packet.getFace().getModY(), packet.getBlockPosition().getZ() + packet.getFace().getModZ());

                    if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
                        player.user.sendPacket(new WrapperPlayServerAcknowledgeBlockChanges(packet.getSequence()));
                    } else {
                        player.resyncPosition(packet.getBlockPosition());
                        player.resyncPosition(facePos);
                    }

                    if (player.platformPlayer != null) {
                        if (packet.getHand() == InteractionHand.MAIN_HAND) {
                            ItemStack mainHand = player.platformPlayer.getInventory().getItemInHand();
                            player.user.sendPacket(new WrapperPlayServerSetSlot(0, player.inventory.stateID, 36 + player.packetStateData.lastSlotSelected, mainHand));
                        } else {
                            ItemStack offHand = player.platformPlayer.getInventory().getItemInOffHand();
                            player.user.sendPacket(new WrapperPlayServerSetSlot(0, player.inventory.stateID, 45, offHand));
                        }
                    }
                } else {
                    player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            WrapperPlayClientUseItem packet = new WrapperPlayClientUseItem(event);
            player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
            player.lastBlockPlaceUseItem = System.currentTimeMillis();
        }

        player.checkManager.onPacketReceive(event);

        if (player.packetStateData.cancelDuplicatePacket) {
            event.setCancelled(true);
            player.packetStateData.cancelDuplicatePacket = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            player.serverOpenedInventoryThisTick = false;
            if (!player.packetStateData.didSendMovementBeforeTickEnd) {
                player.packetStateData.didLastLastMovementIncludePosition = player.packetStateData.didLastMovementIncludePosition;
                player.packetStateData.didLastMovementIncludePosition = false;

                if (!player.inVehicle()) {
                    player.dashableEntities.tick();
                }
            }
            player.packetStateData.didSendMovementBeforeTickEnd = false;
        }

        player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = false;
        player.packetStateData.lastPacketWasTeleport = false;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) return;
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.serverOpenedInventoryThisTick = true);
        }

        if (event.getPacketType() == PacketType.Play.Server.BUNDLE) {
            player.packetStateData.sendingBundlePacket = !player.packetStateData.sendingBundlePacket;
        }

        player.checkManager.onPacketSend(event);
    }

    private static boolean isMojangStupid(GrimPlayer player, PacketReceiveEvent event, WrapperPlayClientPlayerFlying flying) {
        if (player.packetStateData.lastPacketWasTeleport) return false;
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21)) return false;

        final Location location = flying.getLocation();
        final double threshold = player.getMovementThreshold();

        if (!player.packetStateData.lastPacketWasTeleport && flying.hasPositionChanged() && flying.hasRotationChanged() &&
                ((flying.isOnGround() == player.packetStateData.packetPlayerOnGround
                        && (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17) &&
                        player.filterMojangStupidityOnMojangStupidity.distanceSquared(location.getPosition()) < threshold * threshold))
                        || player.inVehicle())) {
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_9)) {
                if (player.isCancelDuplicatePacket()) {
                    player.packetStateData.cancelDuplicatePacket = true;
                }
            } else {
                flying.setLocation(new Location(player.filterMojangStupidityOnMojangStupidity.getX(), player.filterMojangStupidityOnMojangStupidity.getY(), player.filterMojangStupidityOnMojangStupidity.getZ(), location.getYaw(), location.getPitch()));
                event.markForReEncode(true);
            }

            player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = true;

            if (!player.isIgnoreDuplicatePacketRotation()) {
                if (player.yaw != location.getYaw() || player.pitch != location.getPitch()) {
                    player.lastYaw = player.yaw;
                    player.lastPitch = player.pitch;
                }

                player.yaw = location.getYaw();
                player.pitch = location.getPitch();
            }

            player.packetStateData.lastClaimedPosition = location.getPosition();
            return true;
        }
        return false;
    }

    private static void handleFlying(GrimPlayer player, double x, double y, double z, float yaw, float pitch, boolean hasPosition, boolean hasLook, boolean onGround, TeleportAcceptData teleportData, PacketReceiveEvent event) {
        long now = System.currentTimeMillis();

        if (!hasPosition) {
            player.uncertaintyHandler.lastPointThree.reset();
        }

        if (hasLook && (!player.packetStateData.lastPacketWasOnePointSeventeenDuplicate ||
                player.yaw != yaw || player.pitch != pitch)) {
            player.lastYaw = player.yaw;
            player.lastPitch = player.pitch;
        }

        CheckManagerListener.handleQueuedPlaces(player, hasLook, pitch, yaw, now);
        CheckManagerListener.handleQueuedBreaks(player, hasLook, pitch, yaw, now);

        if (hasPosition) {
            player.packetStateData.lastClaimedPosition = new Vector3d(x, y, z);
        }

        if (!hasPosition && onGround != player.packetStateData.packetPlayerOnGround && !player.inVehicle()) {
            boolean canFeasiblyPointThree = Collisions.slowCouldPointThreeHitGround(player, player.x, player.y, player.z);
            if (!canFeasiblyPointThree && !player.compensatedWorld.isNearHardEntity(player.boundingBox.copy().expand(4))
                    || player.clientVelocity.getY() > 0.06 && !player.uncertaintyHandler.wasAffectedByStuckSpeed()) {
                player.getSetbackTeleportUtil().executeForceResync();
            } else {
                player.lastOnGround = onGround;
                player.clientClaimsLastOnGround = onGround;
                player.uncertaintyHandler.onGroundUncertain = true;
            }
        }

        if (!player.packetStateData.lastPacketWasTeleport) {
            player.packetStateData.packetPlayerOnGround = onGround;
        }

        if (hasLook) {
            player.yaw = yaw;
            player.pitch = pitch;
            player.vehicleData.playerPitch = pitch;
            player.vehicleData.playerYaw = yaw;

            float deltaXRot = player.yaw - player.lastYaw;
            float deltaYRot = player.pitch - player.lastPitch;

            final RotationUpdate update = new RotationUpdate(new HeadRotation(player.lastYaw, player.lastPitch), new HeadRotation(player.yaw, player.pitch), deltaXRot, deltaYRot);
            player.checkManager.onRotationUpdate(update);
        }

        if (hasPosition) {
            Vector3d position = new Vector3d(x, y, z);
            Vector3d clampVector = VectorUtils.clampVector(position);
            final PositionUpdate update = new PositionUpdate(new Vector3d(player.x, player.y, player.z), position, onGround, teleportData.getSetback(), teleportData.getTeleportData(), teleportData.isTeleport());

            if (!player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                player.filterMojangStupidityOnMojangStupidity = clampVector;
            }

            if (!player.inVehicle() && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                player.lastX = player.x;
                player.lastY = player.y;
                player.lastZ = player.z;

                player.x = clampVector.getX();
                player.y = clampVector.getY();
                player.z = clampVector.getZ();

                player.checkManager.onPositionUpdate(update);
            } else if (update.isTeleport()) {
                player.getSetbackTeleportUtil().onPredictionComplete(new PredictionComplete(0, update, true));
            }
        }

        player.packetStateData.didLastLastMovementIncludePosition = player.packetStateData.didLastMovementIncludePosition;
        player.packetStateData.didLastMovementIncludePosition = hasPosition;

        if (!player.packetStateData.lastPacketWasTeleport) {
            player.packetStateData.didSendMovementBeforeTickEnd = true;
        }

        player.packetStateData.horseInteractCausedForcedRotation = false;
    }

    private static void handleDigging(GrimPlayer player, PacketReceiveEvent event) {
        player.lastBlockBreak = System.currentTimeMillis();

        final WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
        final DiggingAction action = packet.getAction();

        if (action != DiggingAction.START_DIGGING
                && action != DiggingAction.FINISHED_DIGGING
                && action != DiggingAction.CANCELLED_DIGGING) {
            return;
        }

        final BlockBreak blockBreak = new BlockBreak(player, packet.getBlockPosition(), packet.getBlockFace(), packet.getBlockFaceId(), action, packet.getSequence(), player.compensatedWorld.getBlock(packet.getBlockPosition()));

        player.checkManager.onBlockBreak(blockBreak);

        if (blockBreak.isCancelled()) {
            event.setCancelled(true);
            player.onPacketCancel();
            player.resyncPosition(blockBreak.position, packet.getSequence());
            return;
        }

        player.queuedBreaks.add(blockBreak);

        if (action == DiggingAction.FINISHED_DIGGING && BREAKABLE.apply(blockBreak.block.getType())) {
            player.compensatedWorld.startPredicting();
            player.compensatedWorld.updateBlock(blockBreak.position.x, blockBreak.position.y, blockBreak.position.z, 0);
            player.compensatedWorld.stopPredicting(packet);
        }

        if (action == DiggingAction.START_DIGGING) {
            double damage = BlockBreakSpeed.getBlockDamage(player, blockBreak.block);

            if (damage >= 1) {
                player.compensatedWorld.startPredicting();
                player.blockHistory.add(
                        new BlockModification(
                                player.compensatedWorld.getBlock(blockBreak.position),
                                WrappedBlockState.getByGlobalId(0),
                                blockBreak.position,
                                GrimAPI.INSTANCE.getTickManager().currentTick,
                                BlockModification.Cause.START_DIGGING
                        )
                );
                if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13) && Materials.isWaterSource(player.getClientVersion(), blockBreak.block)) {
                    player.compensatedWorld.updateBlock(blockBreak.position, StateTypes.WATER.createBlockState(CompensatedWorld.blockVersion));
                } else {
                    player.compensatedWorld.updateBlock(blockBreak.position.x, blockBreak.position.y, blockBreak.position.z, 0);
                }
                player.compensatedWorld.stopPredicting(packet);
            }
        }

        player.compensatedWorld.handleBlockBreakPrediction(packet);
    }
}
