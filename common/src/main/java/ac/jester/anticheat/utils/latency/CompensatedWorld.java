package ac.jester.anticheat.utils.latency;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.PacketWorld;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.change.BlockModification;
import ac.jester.anticheat.utils.chunks.Column;
import ac.jester.anticheat.utils.collisions.CollisionData;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.BlockPrediction;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.data.PistonData;
import ac.jester.anticheat.utils.data.ShulkerData;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityShulker;
import ac.jester.anticheat.utils.math.GrimMath;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage;
import com.github.retrooper.packetevents.protocol.world.dimension.DimensionType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.enums.East;
import com.github.retrooper.packetevents.protocol.world.states.enums.Half;
import com.github.retrooper.packetevents.protocol.world.states.enums.North;
import com.github.retrooper.packetevents.protocol.world.states.enums.South;
import com.github.retrooper.packetevents.protocol.world.states.enums.West;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.Getter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompensatedWorld implements PacketWorld {
    public static final ClientVersion blockVersion = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    private static final WrappedBlockState airData = WrappedBlockState.getByGlobalId(blockVersion, 0);
    public final GrimPlayer player;
    public final Long2ObjectMap<Column> chunks;
    public final Set<PistonData> activePistons = new HashSet<>();
    public final Set<ShulkerData> openShulkerBoxes = new HashSet<>();
    @Getter
    private int minHeight = 0;
    @Getter
    private int maxHeight = 256;

    private final Long2ObjectOpenHashMap<BlockPrediction> originalServerBlocks = new Long2ObjectOpenHashMap<>();
    private List<Vector3i> currentlyChangedBlocks = new LinkedList<>();
    private final Int2ObjectMap<List<Vector3i>> serverIsCurrentlyProcessingThesePredictions = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<Pair<Vector3i, DiggingAction>, Vector3d> unackedActions = new Object2ObjectLinkedOpenHashMap<>();
    private boolean isCurrentlyPredicting = false;
    public boolean isRaining = false;

    private final boolean noNegativeBlocks;

    public CompensatedWorld(GrimPlayer player) {
        this.player = player;
        chunks = new Long2ObjectOpenHashMap<>(81, 0.5f);
        noNegativeBlocks = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_16_4);
    }

    public void startPredicting() {
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_18_2)) return;
        this.isCurrentlyPredicting = true;
    }

    public void handlePredictionConfirmation(int prediction) {
        for (Iterator<Int2ObjectMap.Entry<List<Vector3i>>> it = serverIsCurrentlyProcessingThesePredictions.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, List<Vector3i>> iter = it.next();
            if (iter.getKey() <= prediction) {
                applyBlockChanges(iter.getValue());
                it.remove();
            } else {
                break;
            }
        }
    }

    public void handleBlockBreakAck(Vector3i blockPos, int blockState, DiggingAction action, boolean accepted) {
        if (!accepted || action != DiggingAction.START_DIGGING || !unackedActions.containsKey(new Pair<>(blockPos, action))) {
            player.sendTransaction();
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                Vector3d playerPos = unackedActions.remove(new Pair<>(blockPos, action));
                handleAck(blockPos, blockState, playerPos);
            });
        } else {
            unackedActions.remove(new Pair<>(blockPos, action));
        }

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            while (unackedActions.size() >= 50) {
                this.unackedActions.removeFirst();
            }
        });
    }

    private void applyBlockChanges(List<Vector3i> toApplyBlocks) {
        player.sendTransaction();
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> toApplyBlocks.forEach(vector3i -> {
            BlockPrediction predictionData = originalServerBlocks.get(vector3i.getSerializedPosition());

            if (predictionData != null && predictionData.getForBlockUpdate() == toApplyBlocks) {
                originalServerBlocks.remove(vector3i.getSerializedPosition());
                handleAck(vector3i, predictionData.getOriginalBlockId(), predictionData.getPlayerPosition());
            }
        }));
    }

    private void handleAck(Vector3i vector3i, int originalBlockId, Vector3d playerPosition) {
        if (getBlock(vector3i).getGlobalId() != originalBlockId) {
            player.blockHistory.add(
                    new BlockModification(
                            getBlock(vector3i),
                            WrappedBlockState.getByGlobalId(originalBlockId),
                            vector3i,
                            GrimAPI.INSTANCE.getTickManager().currentTick,
                            BlockModification.Cause.HANDLE_NETTY_SYNC_TRANSACTION
                    )
            );
            updateBlock(vector3i.getX(), vector3i.getY(), vector3i.getZ(), originalBlockId);
            WrappedBlockState state = WrappedBlockState.getByGlobalId(blockVersion, originalBlockId);

            if (playerPosition != null && CollisionData.getData(state.getType()).getMovementCollisionBox(player, player.getClientVersion(), state, vector3i.getX(), vector3i.getY(), vector3i.getZ()).isIntersected(player.boundingBox)) {
                player.lastX = player.x;
                player.lastY = player.y;
                player.lastZ = player.z;
                player.x = playerPosition.getX();
                player.y = playerPosition.getY();
                player.z = playerPosition.getZ();
                player.boundingBox = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z);
            }
        }
    }

    public void handleBlockBreakPrediction(WrapperPlayClientPlayerDigging digging) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14_4) && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_18_2)) {
            unackedActions.put(new Pair<>(digging.getBlockPosition(), digging.getAction()), new Vector3d(player.x, player.y, player.z));
        }
    }

    public void stopPredicting(PacketWrapper<?> wrapper) {
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_18_2)) return;
        this.isCurrentlyPredicting = false;

        if (this.currentlyChangedBlocks.isEmpty()) return;

        List<Vector3i> toApplyBlocks = this.currentlyChangedBlocks;
        this.currentlyChangedBlocks = new LinkedList<>();

        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
            int confirmationId = 0;
            if (wrapper instanceof WrapperPlayClientPlayerBlockPlacement) {
                confirmationId = ((WrapperPlayClientPlayerBlockPlacement) wrapper).getSequence();
            } else if (wrapper instanceof WrapperPlayClientUseItem) {
                confirmationId = ((WrapperPlayClientUseItem) wrapper).getSequence();
            } else if (wrapper instanceof WrapperPlayClientPlayerDigging) {
                confirmationId = ((WrapperPlayClientPlayerDigging) wrapper).getSequence();
            }

            serverIsCurrentlyProcessingThesePredictions.put(confirmationId, toApplyBlocks);
        } else {
            GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                player.runSafely(() -> applyBlockChanges(toApplyBlocks));
            });
        }
    }

    public static long chunkPositionToLong(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL);
    }

    public boolean isNearHardEntity(SimpleCollisionBox playerBox) {
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if ((entity.isBoat || entity.type == EntityTypes.SHULKER || entity.isHappyGhast) && player.compensatedEntities.self.getRiding() != entity) {
                SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
                if (box.isIntersected(playerBox)) {
                    return true;
                }
            }
        }

        for (ShulkerData data : openShulkerBoxes) {
            SimpleCollisionBox shulkerCollision = data.getCollision();
            if (playerBox.isCollided(shulkerCollision)) {
                return true;
            }
        }

        for (PistonData data : activePistons) {
            for (SimpleCollisionBox box : data.boxes) {
                if (playerBox.isCollided(box)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static BaseChunk create() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_18)) {
            return new Chunk_v1_18();
        } else if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_16)) {
            return new Chunk_v1_9(0, DataPalette.createForChunk());
        }
        return new Chunk_v1_9(0, new DataPalette(new ListPalette(4), new LegacyFlexibleStorage(4, 4096), PaletteType.CHUNK));
    }

    public void updateBlock(Vector3i pos, WrappedBlockState state) {
        updateBlock(pos.getX(), pos.getY(), pos.getZ(), state.getGlobalId());
    }

    public void updateBlock(int x, int y, int z, int combinedID) {
        Vector3i asVector = new Vector3i(x, y, z);
        BlockPrediction prediction = originalServerBlocks.get(asVector.getSerializedPosition());

        if (isCurrentlyPredicting) {
            if (prediction == null) {
                originalServerBlocks.put(asVector.getSerializedPosition(), new BlockPrediction(currentlyChangedBlocks, asVector, getBlock(asVector).getGlobalId(), new Vector3d(player.x, player.y, player.z)));
            } else {
                prediction.setForBlockUpdate(currentlyChangedBlocks);
            }
            currentlyChangedBlocks.add(asVector);
        }

        if (!isCurrentlyPredicting && prediction != null) {
            prediction.setOriginalBlockId(combinedID);
            return;
        }

        Column column = getChunk(x >> 4, z >> 4);

        int offsetY = y - minHeight;

        if (column != null) {
            if (column.chunks().length <= (offsetY >> 4) || (offsetY >> 4) < 0) return;

            BaseChunk chunk = column.chunks()[offsetY >> 4];

            if (chunk == null) {
                chunk = create();
                column.chunks()[offsetY >> 4] = chunk;

                chunk.set(blockVersion, 0, 0, 0, 0);
            }

            player.pointThreeEstimator.handleChangeBlock(x, y, z, chunk.get(blockVersion, x & 0xF, offsetY & 0xF, z & 0xF));

            chunk.set(blockVersion, x & 0xF, offsetY & 0xF, z & 0xF, combinedID);

            player.pointThreeEstimator.handleChangeBlock(x, y, z, WrappedBlockState.getByGlobalId(blockVersion, combinedID));
        }
    }

    public void tickOpenable(int blockX, int blockY, int blockZ) {
        final WrappedBlockState data = getBlock(blockX, blockY, blockZ);
        final StateType type = data.getType();
        if (Materials.isClientSideOpenableDoor(type, player.getClientVersion())) {
            WrappedBlockState otherDoor = getBlock(blockX,
                    blockY + (data.getHalf() == Half.LOWER ? 1 : -1), blockZ);

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                if (BlockTags.DOORS.contains(otherDoor.getType())) {
                    otherDoor.setOpen(!otherDoor.isOpen());
                    updateBlock(blockX, blockY + (data.getHalf() == Half.LOWER ? 1 : -1), blockZ, otherDoor.getGlobalId());
                }
                data.setOpen(!data.isOpen());
                updateBlock(blockX, blockY, blockZ, data.getGlobalId());
            } else {
                if (data.getHalf() == Half.LOWER) {
                    data.setOpen(!data.isOpen());
                    updateBlock(blockX, blockY, blockZ, data.getGlobalId());
                } else if (BlockTags.DOORS.contains(otherDoor.getType()) && otherDoor.getHalf() == Half.LOWER) {
                    otherDoor.setOpen(!otherDoor.isOpen());
                    updateBlock(blockX, blockY - 1, blockZ, otherDoor.getGlobalId());
                }
            }
        } else if (Materials.isClientSideOpenableTrapdoor(type, player.getClientVersion()) || BlockTags.FENCE_GATES.contains(type)) {
            data.setOpen(!data.isOpen());
            updateBlock(blockX, blockY, blockZ, data.getGlobalId());
        } else if (BlockTags.BUTTONS.contains(type)) {
            data.setPowered(true);
        }
    }

    public void tickPlayerInPistonPushingArea() {
        player.uncertaintyHandler.tick();
        if (player.boundingBox == null) return;

        SimpleCollisionBox expandedBB = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.lastX, player.lastY, player.lastZ, 0.001f, 0.001f);
        expandedBB.expandToAbsoluteCoordinates(player.x, player.y, player.z);
        SimpleCollisionBox playerBox = expandedBB.copy().expand(1);

        double modX = 0;
        double modY = 0;
        double modZ = 0;

        for (PistonData data : activePistons) {
            for (SimpleCollisionBox box : data.boxes) {
                if (playerBox.isCollided(box)) {
                    modX = Math.max(modX, Math.abs(data.direction.getModX() * 0.51D));
                    modY = Math.max(modY, Math.abs(data.direction.getModY() * 0.51D));
                    modZ = Math.max(modZ, Math.abs(data.direction.getModZ() * 0.51D));

                    playerBox.expandMax(modX, modY, modZ);
                    playerBox.expandMin(modX * -1, modY * -1, modZ * -1);

                    if (data.hasSlimeBlock || (data.hasHoneyBlock && player.getClientVersion().isOlderThan(ClientVersion.V_1_15_2))) {
                        player.uncertaintyHandler.slimePistonBounces.add(data.direction);
                    }

                    break;
                }
            }
        }

        for (ShulkerData data : openShulkerBoxes) {
            SimpleCollisionBox shulkerCollision = data.getCollision();

            BlockFace direction;
            if (data.entity == null) {
                WrappedBlockState state = getBlock(data.blockPos.getX(), data.blockPos.getY(), data.blockPos.getZ());
                direction = state.getFacing();
            } else {
                direction = ((PacketEntityShulker) data.entity).facing.getOppositeFace();
            }

            if (direction == null) direction = BlockFace.UP;

            if (direction.getModX() == -1 || direction.getModY() == -1 || direction.getModZ() == -1) {
                shulkerCollision.expandMin(direction.getModX(), direction.getModY(), direction.getModZ());
            } else {
                shulkerCollision.expandMax(direction.getModZ(), direction.getModY(), direction.getModZ());
            }

            if (playerBox.isCollided(shulkerCollision)) {
                modX = Math.max(modX, Math.abs(direction.getModX() * 0.51D));
                modY = Math.max(modY, Math.abs(direction.getModY() * 0.51D));
                modZ = Math.max(modZ, Math.abs(direction.getModZ() * 0.51D));

                playerBox.expandMax(modX, modY, modZ);
                playerBox.expandMin(modX, modY, modZ);

                player.uncertaintyHandler.isSteppingNearShulker = true;
            }
        }

        player.uncertaintyHandler.pistonX.add(modX);
        player.uncertaintyHandler.pistonY.add(modY);
        player.uncertaintyHandler.pistonZ.add(modZ);

        removeInvalidPistonLikeStuff(0);
    }

    public void removeInvalidPistonLikeStuff(int transactionId) {
        if (transactionId != 0) {
            activePistons.removeIf(data -> data.lastTransactionSent < transactionId);
            openShulkerBoxes.removeIf(data -> data.isClosing && data.lastTransactionSent < transactionId);
        } else {
            activePistons.removeIf(PistonData::tickIfGuaranteedFinished);
            openShulkerBoxes.removeIf(ShulkerData::tickIfGuaranteedFinished);
        }
        openShulkerBoxes.removeIf(box -> {
            if (box.blockPos != null) {
                return !Materials.isShulker(getBlock(box.blockPos).getType());
            } else {
                return !player.compensatedEntities.entityMap.containsValue(box.entity);
            }
        });
    }

    public WrappedBlockState getBlock(Vector3i position) {
        return getBlock(position.x, position.y, position.z);
    }

    public WrappedBlockState getBlock(int x, int y, int z) {
        if (noNegativeBlocks && y < 0) return airData;

        try {
            Column column = getChunk(x >> 4, z >> 4);

            y -= minHeight;
            if (column == null || y < 0 || (y >> 4) >= column.chunks().length) return airData;

            BaseChunk chunk = column.chunks()[y >> 4];
            if (chunk != null) {
                return chunk.get(blockVersion, x & 0xF, y & 0xF, z & 0xF);
            }
        } catch (Exception ignored) {
        }

        return airData;
    }

    @Override
    public int getBlockStateId(int x, int y, int z) {
        if (noNegativeBlocks && y < 0) return -1;

        try {
            Column column = getChunk(x >> 4, z >> 4);

            y -= minHeight;
            if (column == null || y < 0 || (y >> 4) >= column.chunks().length) return -2;

            BaseChunk chunk = column.chunks()[y >> 4];
            if (chunk != null) {
                return chunk.getBlockId(x & 0xF, y & 0xF, z & 0xF);
            }
        } catch (Exception ignored) {
        }
        return -3;
    }

    public int getRawPowerAtState(BlockFace face, int x, int y, int z) {
        WrappedBlockState block = getBlock(x, y, z);

        if (block.getType() == StateTypes.REDSTONE_BLOCK) {
            return 15;
        } else if (block.getType() == StateTypes.DETECTOR_RAIL) {
            return block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.REDSTONE_TORCH) {
            return face != BlockFace.UP && block.isLit() ? 15 : 0;
        } else if (block.getType() == StateTypes.REDSTONE_WIRE) {
            BlockFace needed = face.getOppositeFace();

            BlockFace badOne = needed.getCW();
            BlockFace badTwo = needed.getCCW();

            boolean isPowered = false;
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                switch (needed) {
                    case DOWN:
                        isPowered = true;
                        break;
                    case NORTH:
                        isPowered = block.getNorth() == North.TRUE;
                        if (isPowered && (badOne == BlockFace.NORTH || badTwo == BlockFace.NORTH)) {
                            return 0;
                        }
                        break;
                    case SOUTH:
                        isPowered = block.getSouth() == South.TRUE;
                        if (isPowered && (badOne == BlockFace.SOUTH || badTwo == BlockFace.SOUTH)) {
                            return 0;
                        }
                        break;
                    case WEST:
                        isPowered = block.getWest() == West.TRUE;
                        if (isPowered && (badOne == BlockFace.WEST || badTwo == BlockFace.WEST)) {
                            return 0;
                        }
                        break;
                    case EAST:
                        isPowered = block.getEast() == East.TRUE;
                        if (isPowered && (badOne == BlockFace.EAST || badTwo == BlockFace.EAST)) {
                            return 0;
                        }
                        break;
                }
            } else {
                isPowered = true;
            }

            return isPowered ? block.getPower() : 0;
        } else if (block.getType() == StateTypes.REDSTONE_WALL_TORCH) {
            return block.getFacing() != face && block.isLit() ? 15 : 0;
        } else if (block.getType() == StateTypes.DAYLIGHT_DETECTOR) {
            return block.getPower();
        } else if (block.getType() == StateTypes.OBSERVER) {
            return block.getFacing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.REPEATER) {
            return block.getFacing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.LECTERN) {
            return block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.TARGET) {
            return block.getPower();
        }

        return 0;
    }

    public int getDirectSignalAtState(BlockFace face, int x, int y, int z) {
        WrappedBlockState block = getBlock(x, y, z);

        if (block.getType() == StateTypes.DETECTOR_RAIL) {
            boolean isPowered = block.hasProperty(StateValue.POWERED) && block.isPowered();
            return face == BlockFace.UP && isPowered ? 15 : 0;
        } else if (block.getType() == StateTypes.REDSTONE_TORCH) {
            return face != BlockFace.UP && block.isLit() ? 15 : 0;
        } else if (block.getType() == StateTypes.LEVER || BlockTags.BUTTONS.contains(block.getType())) {
            return block.getFacing().getOppositeFace() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.REDSTONE_WALL_TORCH) {
            return face == BlockFace.DOWN && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.LECTERN) {
            return face == BlockFace.UP && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.OBSERVER) {
            return block.getFacing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.REPEATER) {
            return block.getFacing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == StateTypes.REDSTONE_WIRE) {
            BlockFace needed = face.getOppositeFace();

            BlockFace badOne = needed.getCW();
            BlockFace badTwo = needed.getCCW();

            boolean isPowered = false;
            switch (needed) {
                case DOWN:
                case UP:
                    break;
                case NORTH:
                    isPowered = block.getNorth() == North.TRUE;
                    if (isPowered && (badOne == BlockFace.NORTH || badTwo == BlockFace.NORTH)) {
                        return 0;
                    }
                    break;
                case SOUTH:
                    isPowered = block.getSouth() == South.TRUE;
                    if (isPowered && (badOne == BlockFace.SOUTH || badTwo == BlockFace.SOUTH)) {
                        return 0;
                    }
                    break;
                case WEST:
                    isPowered = block.getWest() == West.TRUE;
                    if (isPowered && (badOne == BlockFace.WEST || badTwo == BlockFace.WEST)) {
                        return 0;
                    }
                    break;
                case EAST:
                    isPowered = block.getEast() == East.TRUE;
                    if (isPowered && (badOne == BlockFace.EAST || badTwo == BlockFace.EAST)) {
                        return 0;
                    }
                    break;
            }

            return isPowered ? block.getPower() : 0;
        }

        return 0;
    }

    public Column getChunk(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        return chunks.get(chunkPosition);
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        return chunks.containsKey(chunkPosition);
    }

    public void addToCache(Column chunk, int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> chunks.put(chunkPosition, chunk));
    }

    public StateType getBlockType(double x, double y, double z) {
        return getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).getType();
    }

    public WrappedBlockState getBlock(double x, double y, double z) {
        return getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public double getFluidLevelAt(int x, int y, int z) {
        return Math.max(getWaterFluidLevelAt(x, y, z), getLavaFluidLevelAt(x, y, z));
    }

    public boolean isWaterSourceBlock(int x, int y, int z) {
        WrappedBlockState bukkitBlock = getBlock(x, y, z);
        return Materials.isWaterSource(player.getClientVersion(), bukkitBlock);
    }

    public boolean containsLiquid(SimpleCollisionBox var0) {
        return Collisions.hasMaterial(player, var0, data -> Materials.isWater(player.getClientVersion(), data.first()) || data.first().getType() == StateTypes.LAVA);
    }

    public double getLavaFluidLevelAt(int x, int y, int z) {
        WrappedBlockState magicBlockState = getBlock(x, y, z);
        WrappedBlockState magicBlockStateAbove = getBlock(x, y + 1, z);

        if (magicBlockState.getType() != StateTypes.LAVA) return 0;
        if (magicBlockStateAbove.getType() == StateTypes.LAVA) return 1;

        int level = magicBlockState.getLevel();

        if (level >= 8) {
            return 8 / 9f;
        }

        return (8 - level) / 9f;
    }

    public boolean containsLava(SimpleCollisionBox var0) {
        return Collisions.hasMaterial(player, var0, data -> data.first().getType() == StateTypes.LAVA);
    }

    public double getWaterFluidLevelAt(double x, double y, double z) {
        return getWaterFluidLevelAt(GrimMath.floor(x), GrimMath.floor(y), GrimMath.floor(z));
    }

    public double getWaterFluidLevelAt(int x, int y, int z) {
        WrappedBlockState wrappedBlock = getBlock(x, y, z);
        boolean isWater = Materials.isWater(player.getClientVersion(), wrappedBlock);

        if (!isWater) return 0;

        if (Materials.isWater(player.getClientVersion(), getBlock(x, y + 1, z))) {
            return 1;
        }

        if (wrappedBlock.getType() == StateTypes.WATER) {
            int level = wrappedBlock.getLevel();

            if ((level & 0x8) == 8) return 8 / 9f;

            return (8 - level) / 9f;
        }

        return 8 / 9F;
    }

    public void removeChunkLater(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> chunks.remove(chunkPosition));
    }

    public void setDimension(DimensionType dimension, User user) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_17)) return;

        minHeight = dimension.getMinY();
        maxHeight = minHeight + dimension.getHeight();
    }

    public WrappedBlockState getBlock(Vector3dm aboveCCWPos) {
        return getBlock(aboveCCWPos.getX(), aboveCCWPos.getY(), aboveCCWPos.getZ());
    }
}
