package ac.jester.anticheat.utils.nmsutil;

import ac.jester.anticheat.events.packets.PacketWorldBorder;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.blockeffects.BlockEffectsResolver;
import ac.jester.anticheat.predictionengine.blockeffects.impl.BlockEffectsResolverV1_21_10;
import ac.jester.anticheat.predictionengine.blockeffects.impl.BlockEffectsResolverV1_21_2;
import ac.jester.anticheat.predictionengine.blockeffects.impl.BlockEffectsResolverV1_21_4;
import ac.jester.anticheat.predictionengine.blockeffects.impl.BlockEffectsResolverV1_21_5;
import ac.jester.anticheat.predictionengine.blockeffects.impl.BlockEffectsResolverV1_21_6;
import ac.jester.anticheat.utils.chunks.Column;
import ac.jester.anticheat.utils.collisions.CollisionData;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.tags.SyncedTags;
import ac.jester.anticheat.utils.latency.CompensatedWorld;
import ac.jester.anticheat.utils.math.Location;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.math.VectorUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.protocol.world.Direction;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@UtilityClass
public final class Collisions {
    public static final double COLLISION_EPSILON = 1.0E-7;

    private static final boolean IS_FOURTEEN = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14);
    private static final List<List<Axis>> allAxisCombinations = Arrays.asList(
            Arrays.asList(Axis.Y, Axis.X, Axis.Z),
            Arrays.asList(Axis.Y, Axis.Z, Axis.X),

            Arrays.asList(Axis.X, Axis.Y, Axis.Z),
            Arrays.asList(Axis.X, Axis.Z, Axis.Y),

            Arrays.asList(Axis.Z, Axis.X, Axis.Y),
            Arrays.asList(Axis.Z, Axis.Y, Axis.X));
    private static final List<List<Axis>> nonStupidityCombinations = Arrays.asList(
            Arrays.asList(Axis.Y, Axis.X, Axis.Z),
            Arrays.asList(Axis.Y, Axis.Z, Axis.X));

    public static boolean slowCouldPointThreeHitGround(GrimPlayer player, double x, double y, double z) {
        SimpleCollisionBox oldBB = player.boundingBox;
        player.boundingBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player, x, y, z, 0.6f, 0.06f);

        double movementThreshold = player.getMovementThreshold();
        double posXZ = collide(player, movementThreshold, -movementThreshold, movementThreshold).getY();
        double negXNegZ = collide(player, -movementThreshold, -movementThreshold, -movementThreshold).getY();
        double posXNegZ = collide(player, movementThreshold, -movementThreshold, -movementThreshold).getY();
        double posZNegX = collide(player, -movementThreshold, -movementThreshold, movementThreshold).getY();

        player.boundingBox = oldBB;
        return negXNegZ != -movementThreshold || posXNegZ != -movementThreshold || posXZ != -movementThreshold || posZNegX != -movementThreshold;
    }

    public static Vector3dm collide(GrimPlayer player, double desiredX, double desiredY, double desiredZ) {
        return collide(player, desiredX, desiredY, desiredZ, desiredY, null);
    }

    public static Vector3dm collide(GrimPlayer player, double desiredX, double desiredY, double desiredZ, double clientVelY, VectorData data) {
        if (desiredX == 0 && desiredY == 0 && desiredZ == 0) return new Vector3dm();

        final SimpleCollisionBox grabBoxesBB = player.boundingBox.copy();
        final double stepUpHeight = player.getMaxUpStep();

        if (desiredX == 0.0 && desiredZ == 0.0) {
            if (desiredY > 0.0) {
                grabBoxesBB.maxY += desiredY;
            } else {
                grabBoxesBB.minY += desiredY;
            }
        } else {
            if (stepUpHeight > 0.0 && (player.lastOnGround || desiredY < 0 || clientVelY < 0)) {
                if (desiredY <= 0.0) {
                    grabBoxesBB.expandToCoordinate(desiredX, desiredY, desiredZ);
                    grabBoxesBB.maxY += stepUpHeight;
                } else {
                    grabBoxesBB.expandToCoordinate(desiredX, Math.max(stepUpHeight, desiredY), desiredZ);
                }
            } else {
                grabBoxesBB.expandToCoordinate(desiredX, desiredY, desiredZ);
            }
        }

        List<SimpleCollisionBox> desiredMovementCollisionBoxes = new ArrayList<>();
        getCollisionBoxes(player, grabBoxesBB, desiredMovementCollisionBoxes, false);

        double bestInput = Double.MAX_VALUE;
        Vector3dm bestOrderResult = null;

        Vector3dm bestTheoreticalCollisionResult = VectorUtils.cutBoxToVector(player.actualMovement, new SimpleCollisionBox(0, Math.min(0, desiredY), 0, desiredX, Math.max(stepUpHeight, desiredY), desiredZ).sort());
        int zeroCount = (desiredX == 0 ? 1 : 0) + (desiredY == 0 ? 1 : 0) + (desiredZ == 0 ? 1 : 0);

        for (List<Axis> order : (data != null && data.isZeroPointZeroThree() ? allAxisCombinations : nonStupidityCombinations)) {
            Vector3dm collisionResult = collideBoundingBoxLegacy(new Vector3dm(desiredX, desiredY, desiredZ), player.boundingBox, desiredMovementCollisionBoxes, order);

            boolean movingIntoGroundReal = player.pointThreeEstimator.closeEnoughToGroundToStepWithPointThree(data, clientVelY) || collisionResult.getY() != desiredY && (desiredY < 0 || clientVelY < 0);
            boolean movingIntoGround = player.lastOnGround || movingIntoGroundReal;

            final boolean disallowStepping = player.getSetbackTeleportUtil().getRequiredSetBack() != null && player.getSetbackTeleportUtil().getRequiredSetBack().getTicksComplete() == 1;
            if (!disallowStepping && stepUpHeight > 0.0F && movingIntoGround && (collisionResult.getX() != desiredX || collisionResult.getZ() != desiredZ)) {
                player.uncertaintyHandler.isStepMovement = true;
                if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21)) {
                    SimpleCollisionBox startingOffsetBox = movingIntoGroundReal ? player.boundingBox.copy().offset(0.0, collisionResult.getY(), 0.0) : player.boundingBox.copy();
                    SimpleCollisionBox offsetByHorizAndStepBox = startingOffsetBox.copy().expandToCoordinate(desiredX, stepUpHeight, desiredZ);
                    if (!movingIntoGroundReal) {
                        offsetByHorizAndStepBox = offsetByHorizAndStepBox.copy().expandToCoordinate(0.0, -1.0E-5F, 0.0);
                    }

                    final List<SimpleCollisionBox> stepCollisions = new ArrayList<>();
                    getCollisionBoxes(player, offsetByHorizAndStepBox, stepCollisions, false);
                    final float[] stepHeights = collectStepHeights(startingOffsetBox, stepCollisions, (float) stepUpHeight, (float) collisionResult.getY());

                    for (float stepHeight : stepHeights) {
                        Vector3dm vec3d2 = collideBoundingBoxLegacy(new Vector3dm(desiredX, stepHeight, desiredZ), startingOffsetBox, stepCollisions, order);
                        if (getHorizontalDistanceSqr(vec3d2) > getHorizontalDistanceSqr(collisionResult)) {
                            final double d = player.boundingBox.minY - startingOffsetBox.minY;
                            collisionResult = vec3d2.add(new Vector3dm(0.0, -d, 0.0));
                            break;
                        }
                    }
                } else {
                    Vector3dm regularStepUp = collideBoundingBoxLegacy(new Vector3dm(desiredX, stepUpHeight, desiredZ), player.boundingBox, desiredMovementCollisionBoxes, order);

                    if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8)) {
                        Vector3dm stepUpBugFix = collideBoundingBoxLegacy(new Vector3dm(0, stepUpHeight, 0), player.boundingBox.copy().expandToCoordinate(desiredX, 0, desiredZ), desiredMovementCollisionBoxes, order);
                        if (stepUpBugFix.getY() < stepUpHeight) {
                            Vector3dm stepUpBugFixResult = collideBoundingBoxLegacy(new Vector3dm(desiredX, 0, desiredZ), player.boundingBox.copy().offset(0, stepUpBugFix.getY(), 0), desiredMovementCollisionBoxes, order).add(stepUpBugFix);
                            if (getHorizontalDistanceSqr(stepUpBugFixResult) > getHorizontalDistanceSqr(regularStepUp)) {
                                regularStepUp = stepUpBugFixResult;
                            }
                        }
                    }

                    if (getHorizontalDistanceSqr(regularStepUp) > getHorizontalDistanceSqr(collisionResult)) {
                        collisionResult = regularStepUp.add(collideBoundingBoxLegacy(new Vector3dm(0, -regularStepUp.getY() + (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14) ? desiredY : 0), 0), player.boundingBox.copy().offset(regularStepUp.getX(), regularStepUp.getY(), regularStepUp.getZ()), desiredMovementCollisionBoxes, order));
                    }
                }
            }

            double resultAccuracy = collisionResult.distanceSquared(bestTheoreticalCollisionResult);

            if (player.wouldCollisionResultFlagGroundSpoof(desiredY, collisionResult.getY())) {
                resultAccuracy += 1;
            }

            if (resultAccuracy < bestInput) {
                bestOrderResult = collisionResult;
                bestInput = resultAccuracy;
                if (resultAccuracy < 0.00001 * 0.00001) break;
                if (zeroCount >= 2) break;
            }
        }
        return bestOrderResult;
    }

    private static float[] collectStepHeights(SimpleCollisionBox collisionBox, List<SimpleCollisionBox> collisions, float stepHeight, float collideY) {
        final FloatSet floatSet = new FloatArraySet(4);

        for (SimpleCollisionBox blockBox : collisions) {
            for (double possibleStepY : blockBox.getYPointPositions()) {
                float yDiff = (float) (possibleStepY - collisionBox.minY);
                if (!(yDiff < 0.0F) && yDiff != collideY) {
                    if (yDiff > stepHeight) {
                        break;
                    }

                    floatSet.add(yDiff);
                }
            }
        }

        float[] fs = floatSet.toFloatArray();
        FloatArrays.unstableSort(fs);
        return fs;
    }

    public static boolean addWorldBorder(GrimPlayer player, SimpleCollisionBox wantedBB, List<SimpleCollisionBox> listOfBlocks, boolean onlyCheckCollide) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8)) {
            PacketWorldBorder border = player.checkManager.getPacketCheck(PacketWorldBorder.class);

            double minX = Math.floor(border.getMinX());
            double minZ = Math.floor(border.getMinZ());
            double maxX = Math.ceil(border.getMaxX());
            double maxZ = Math.ceil(border.getMaxZ());

            double toMinX = player.lastX - minX;
            double toMaxX = maxX - player.lastX;
            double minimumInXDirection = Math.min(toMinX, toMaxX);

            double toMinZ = player.lastZ - minZ;
            double toMaxZ = maxZ - player.lastZ;
            double minimumInZDirection = Math.min(toMinZ, toMaxZ);

            double distanceToBorder = Math.min(minimumInXDirection, minimumInZDirection);

            if (distanceToBorder < 16 && player.lastX > minX && player.lastX < maxX && player.lastZ > minZ && player.lastZ < maxZ) {
                if (listOfBlocks == null) listOfBlocks = new ArrayList<>();

                listOfBlocks.add(new SimpleCollisionBox(minX - 10, Double.NEGATIVE_INFINITY, maxZ, maxX + 10, Double.POSITIVE_INFINITY, maxZ, false));
                listOfBlocks.add(new SimpleCollisionBox(minX - 10, Double.NEGATIVE_INFINITY, minZ, maxX + 10, Double.POSITIVE_INFINITY, minZ, false));
                listOfBlocks.add(new SimpleCollisionBox(maxX, Double.NEGATIVE_INFINITY, minZ - 10, maxX, Double.POSITIVE_INFINITY, maxZ + 10, false));
                listOfBlocks.add(new SimpleCollisionBox(minX, Double.NEGATIVE_INFINITY, minZ - 10, minX, Double.POSITIVE_INFINITY, maxZ + 10, false));

                if (onlyCheckCollide) {
                    for (SimpleCollisionBox box : listOfBlocks) {
                        if (box.isIntersected(wantedBB)) return true;
                    }
                }
            }
        }
        return false;
    }

    // This is mostly taken from Tuinity collisions
    public static boolean getCollisionBoxes(GrimPlayer player, SimpleCollisionBox wantedBB, List<SimpleCollisionBox> listOfBlocks, boolean onlyCheckCollide) {
        SimpleCollisionBox expandedBB = wantedBB.copy();

        boolean collided = addWorldBorder(player, wantedBB, listOfBlocks, onlyCheckCollide);
        if (onlyCheckCollide && collided) return true;

        int minBlockX = (int) Math.floor(expandedBB.minX - COLLISION_EPSILON) - 1;
        int maxBlockX = (int) Math.floor(expandedBB.maxX + COLLISION_EPSILON) + 1;
        int minBlockY = (int) Math.floor(expandedBB.minY - COLLISION_EPSILON) - 1;
        int maxBlockY = (int) Math.floor(expandedBB.maxY + COLLISION_EPSILON) + 1;
        int minBlockZ = (int) Math.floor(expandedBB.minZ - COLLISION_EPSILON) - 1;
        int maxBlockZ = (int) Math.floor(expandedBB.maxZ + COLLISION_EPSILON) + 1;

        final int minSection = player.compensatedWorld.getMinHeight() >> 4;
        final int minBlock = minSection << 4;
        final int maxBlock = player.compensatedWorld.getMaxHeight() - 1;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        int minYIterate = Math.max(minBlock, minBlockY);
        int maxYIterate = Math.min(maxBlock, maxBlockY);

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            int minZ = currChunkZ == minChunkZ ? minBlockZ & 15 : 0;
            int maxZ = currChunkZ == maxChunkZ ? maxBlockZ & 15 : 15;

            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                int minX = currChunkX == minChunkX ? minBlockX & 15 : 0;
                int maxX = currChunkX == maxChunkX ? maxBlockX & 15 : 15;

                int chunkXGlobalPos = currChunkX << 4;
                int chunkZGlobalPos = currChunkZ << 4;

                Column chunk = player.compensatedWorld.getChunk(currChunkX, currChunkZ);
                if (chunk == null) continue;

                BaseChunk[] sections = chunk.chunks();

                for (int y = minYIterate; y <= maxYIterate; ++y) {
                    int sectionIndex = (y >> 4) - minSection;

                    BaseChunk section = sections[sectionIndex];

                    if (section == null || (IS_FOURTEEN && section.isEmpty())) {
                        y = (y & ~15) + 15;
                        continue;
                    }

                    for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                        for (int currX = minX; currX <= maxX; ++currX) {
                            int x = currX | chunkXGlobalPos;
                            int z = currZ | chunkZGlobalPos;

                            WrappedBlockState data = section.get(CompensatedWorld.blockVersion, x & 0xF, y & 0xF, z & 0xF, false);

                            if (data.getGlobalId() == 0) continue;

                            int edgeCount = ((x == minBlockX || x == maxBlockX) ? 1 : 0) +
                                    ((y == minBlockY || y == maxBlockY) ? 1 : 0) +
                                    ((z == minBlockZ || z == maxBlockZ) ? 1 : 0);

                            final StateType type = data.getType();
                            if (edgeCount != 3 && (edgeCount != 1 || Materials.isShapeExceedsCube(type))
                                    && (edgeCount != 2 || type == StateTypes.PISTON_HEAD)) {
                                final CollisionBox collisionBox = CollisionData.getData(type).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z);
                                if (!onlyCheckCollide) {
                                    collisionBox.downCast(listOfBlocks);
                                } else if (collisionBox.isCollided(wantedBB)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    public static Vector3dm collideBoundingBoxLegacy(Vector3dm toCollide, SimpleCollisionBox
            box, List<SimpleCollisionBox> desiredMovementCollisionBoxes, List<Axis> order) {
        double x = toCollide.getX();
        double y = toCollide.getY();
        double z = toCollide.getZ();

        SimpleCollisionBox setBB = box.copy();

        for (Axis axis : order) {
            if (axis == Axis.X) {
                for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                    x = bb.collideX(setBB, x);
                }
                setBB.offset(x, 0.0D, 0.0D);
            } else if (axis == Axis.Y) {
                for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                    y = bb.collideY(setBB, y);
                }
                setBB.offset(0.0D, y, 0.0D);
            } else if (axis == Axis.Z) {
                for (SimpleCollisionBox bb : desiredMovementCollisionBoxes) {
                    z = bb.collideZ(setBB, z);
                }
                setBB.offset(0.0D, 0.0D, z);
            }
        }

        return new Vector3dm(x, y, z);
    }

    public static boolean isEmpty(GrimPlayer player, SimpleCollisionBox playerBB) {
        return !getCollisionBoxes(player, playerBB, null, true);
    }

    public static double getHorizontalDistanceSqr(Vector3dm vector) {
        return vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
    }

    public static Vector3dm maybeBackOffFromEdge(Vector3dm vec3, GrimPlayer player, boolean overrideVersion) {
        if (!player.isFlying && player.isSneaking && isAboveGround(player)) {
            double x = vec3.getX();
            double z = vec3.getZ();

            double maxStepDown = overrideVersion || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_11) ? -player.getMaxUpStep() : -1 + COLLISION_EPSILON;

            while (x != 0.0 && isEmpty(player, player.boundingBox.copy().offset(x, maxStepDown, 0.0))) {
                if (x < 0.05D && x >= -0.05D) {
                    x = 0.0D;
                } else if (x > 0.0D) {
                    x -= 0.05D;
                } else {
                    x += 0.05D;
                }
            }
            while (z != 0.0 && isEmpty(player, player.boundingBox.copy().offset(0.0, maxStepDown, z))) {
                if (z < 0.05D && z >= -0.05D) {
                    z = 0.0D;
                } else if (z > 0.0D) {
                    z -= 0.05D;
                } else {
                    z += 0.05D;
                }
            }
            while (x != 0.0 && z != 0.0 && isEmpty(player, player.boundingBox.copy().offset(x, maxStepDown, z))) {
                if (x < 0.05D && x >= -0.05D) {
                    x = 0.0D;
                } else if (x > 0.0D) {
                    x -= 0.05D;
                } else {
                    x += 0.05D;
                }

                if (z < 0.05D && z >= -0.05D) {
                    z = 0.0D;
                } else if (z > 0.0D) {
                    z -= 0.05D;
                } else {
                    z += 0.05D;
                }
            }
            vec3 = new Vector3dm(x, vec3.getY(), z);
        }

        return vec3;
    }

    public static boolean isAboveGround(GrimPlayer player) {
        return player.lastOnGround || (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_16_2) && (player.fallDistance < player.getMaxUpStep() &&
                !isEmpty(player, player.boundingBox.copy().offset(0.0, player.fallDistance - player.getMaxUpStep(), 0.0))));
    }

    public static void handleInsideBlocks(GrimPlayer player) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2)) return;
        double expandAmount = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19_4) ? 1e-5 : 0.001;
        SimpleCollisionBox aABB = (player.inVehicle()
                ? GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z)
                : player.boundingBox.copy()).expand(-expandAmount);

        Location blockPos = new Location(null, aABB.minX, aABB.minY, aABB.minZ);
        Location blockPos2 = new Location(null, aABB.maxX, aABB.maxY, aABB.maxZ);

        if (CheckIfChunksLoaded.areChunksUnloadedAt(player, blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ(), blockPos2.getBlockX(), blockPos2.getBlockY(), blockPos2.getBlockZ()))
            return;

        for (int blockX = blockPos.getBlockX(); blockX <= blockPos2.getBlockX(); ++blockX) {
            for (int blockY = blockPos.getBlockY(); blockY <= blockPos2.getBlockY(); ++blockY) {
                for (int blockZ = blockPos.getBlockZ(); blockZ <= blockPos2.getBlockZ(); ++blockZ) {
                    WrappedBlockState block = player.compensatedWorld.getBlock(blockX, blockY, blockZ);
                    StateType blockType = block.getType();

                    if (blockType.isAir()) {
                        continue;
                    }

                    onInsideBlock(player, blockType, block, blockX, blockY, blockZ, true);
                }
            }
        }
    }

    public static void onInsideBlock(GrimPlayer player, StateType blockType, WrappedBlockState block, int blockX, int blockY, int blockZ, boolean magic) {
        if (blockType == StateTypes.COBWEB) {
            if (player.compensatedEntities.hasPotionEffect(PotionTypes.WEAVING)) {
                player.stuckSpeedMultiplier = new Vector3dm(0.5, 0.25, 0.5);
            } else {
                player.stuckSpeedMultiplier = new Vector3dm(0.25, 0.05f, 0.25);
            }
        }

        if (blockType == StateTypes.SWEET_BERRY_BUSH
                && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)) {
            player.stuckSpeedMultiplier = new Vector3dm(0.8f, 0.75, 0.8f);
        }

        if (blockType == StateTypes.POWDER_SNOW && blockX == Math.floor(player.x) && blockY == Math.floor(player.y) && blockZ == Math.floor(player.z)
                && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17)) {
            player.stuckSpeedMultiplier = new Vector3dm(0.9f, 1.5, 0.9f);
        }

        if (blockType == StateTypes.SOUL_SAND && player.getClientVersion().isOlderThan(ClientVersion.V_1_15)) {
            player.clientVelocity.setX(player.clientVelocity.getX() * 0.4D);
            player.clientVelocity.setZ(player.clientVelocity.getZ() * 0.4D);
        }

        if (blockType == StateTypes.LAVA && player.getClientVersion().isOlderThan(ClientVersion.V_1_16) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)) {
            player.wasTouchingLava = true;
        }

        if (blockType == StateTypes.BUBBLE_COLUMN && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13) && magic) {
            WrappedBlockState blockAbove = player.compensatedWorld.getBlock(blockX, blockY + 1, blockZ);

            if (player.inVehicle() && player.compensatedEntities.self.getRiding().isBoat) {
                if (!blockAbove.getType().isAir()) {
                    if (block.isDrag()) {
                        player.clientVelocity.setY(Math.max(-0.3D, player.clientVelocity.getY() - 0.03D));
                    } else {
                        player.clientVelocity.setY(Math.min(0.7D, player.clientVelocity.getY() + 0.06D));
                    }
                }
            } else {
                if (blockAbove.getType().isAir()) {
                    for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
                        if (block.isDrag()) {
                            vector.vector.setY(Math.max(-0.9D, vector.vector.getY() - 0.03D));
                        } else {
                            vector.vector.setY(Math.min(1.8D, vector.vector.getY() + 0.1D));
                        }
                    }
                } else {
                    for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
                        if (block.isDrag()) {
                            vector.vector.setY(Math.max(-0.3D, vector.vector.getY() - 0.03D));
                        } else {
                            vector.vector.setY(Math.min(0.7D, vector.vector.getY() + 0.06D));
                        }
                    }
                }
            }

            player.fallDistance = 0;
        }

        if (blockType == StateTypes.HONEY_BLOCK && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_15)) {
            if (isSlidingDown(player.clientVelocity, player, blockX, blockY, blockZ)) {
                if (getOldDeltaY(player, player.clientVelocity.getY()) < -0.13D) {
                    double d0 = -0.05 / getOldDeltaY(player, player.clientVelocity.getY());
                    player.clientVelocity.setX(player.clientVelocity.getX() * d0);
                    player.clientVelocity.setY(getNewDeltaY(player, -0.05D));
                    player.clientVelocity.setZ(player.clientVelocity.getZ() * d0);
                } else {
                    player.clientVelocity.setY(getNewDeltaY(player, -0.05D));
                }
            }

            player.fallDistance = 0;
        }
    }

    public static void applyEffectsFromBlocks(GrimPlayer player) {
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2)) {
            return;
        }

        if (player.stuckSpeedMultiplier.getX() < 0.99) {
            player.uncertaintyHandler.lastStuckSpeedMultiplier.reset();
        }

        player.stuckSpeedMultiplier = new Vector3dm(1, 1, 1);
        player.finalMovementsThisTick.clear();

        Vector3d from = new Vector3d(player.lastX, player.lastY, player.lastZ);
        Vector3d to = new Vector3d(player.x, player.y, player.z);

        ClientVersion clientVersion = player.getClientVersion();
        if (clientVersion.isOlderThan(ClientVersion.V_1_21_5)) {
            player.finalMovementsThisTick.add(new GrimPlayer.Movement(from, to));
        } else if (clientVersion.isNewerThanOrEquals(ClientVersion.V_1_21_5)) {
            player.finalMovementsThisTick.addAll(player.movementThisTick);
            player.movementThisTick.clear();

            if (player.finalMovementsThisTick.isEmpty()) {
                player.finalMovementsThisTick.add(new GrimPlayer.Movement(from, to));
            } else if (player.finalMovementsThisTick.get(player.finalMovementsThisTick.size() - 1).to().distanceSquared(to) > 9.9999994E-11F) {
                player.finalMovementsThisTick.add(new GrimPlayer.Movement(player.finalMovementsThisTick.get(player.finalMovementsThisTick.size() - 1).to(), to));
            }
        }

        Collisions.resolveBlockEffects(player, player.finalMovementsThisTick);

        if (player.stuckSpeedMultiplier.getX() < 0.9) {
            player.fallDistance = 0;
        }

        if (player.isFlying) {
            player.stuckSpeedMultiplier = new Vector3dm(1, 1, 1);
        }
    }

    public static void resolveBlockEffects(GrimPlayer player, Vector3d from, Vector3d to) {
        Collisions.resolveBlockEffects(player, List.of(new GrimPlayer.Movement(from, to)));
    }

    public static void resolveBlockEffects(GrimPlayer player, List<GrimPlayer.Movement> movements) {
        ClientVersion version = player.getClientVersion();
        BlockEffectsResolver resolver;

        if (version == ClientVersion.V_1_21_2) {
            resolver = BlockEffectsResolverV1_21_2.INSTANCE;
        } else if (version == ClientVersion.V_1_21_4) {
            resolver = BlockEffectsResolverV1_21_4.INSTANCE;
        } else if (version == ClientVersion.V_1_21_5) {
            resolver = BlockEffectsResolverV1_21_5.INSTANCE;
        } else if (version.isNewerThanOrEquals(ClientVersion.V_1_21_6) && version.isOlderThanOrEquals(ClientVersion.V_1_21_7)) {
            resolver = BlockEffectsResolverV1_21_6.INSTANCE;
        } else {
            resolver = BlockEffectsResolverV1_21_10.INSTANCE;
        }

        resolver.applyEffectsFromBlocks(player, movements);
    }

    private static double getOldDeltaY(GrimPlayer player, double value) {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2) ? value / 0.98F + 0.08 : value;
    }

    private static double getNewDeltaY(GrimPlayer player, double value) {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2) ? (value - 0.08) * 0.98F : value;
    }

    private static boolean isSlidingDown(Vector3dm vector, GrimPlayer player, int locationX, int locationY,
                                         int locationZ) {
        if (player.onGround) {
            return false;
        } else if (player.y > (double) locationY + 0.9375D - COLLISION_EPSILON) {
            return false;
        } else if (getOldDeltaY(player, vector.getY()) >= -0.08D) {
            return false;
        } else {
            double d0 = Math.abs(locationX + 0.5D - player.lastX);
            double d1 = Math.abs(locationZ + 0.5D - player.lastZ);
            double d2 = 0.4375D + ((player.pose.width) / 2.0F);
            return d0 + COLLISION_EPSILON > d2 || d1 + COLLISION_EPSILON > d2;
        }
    }

    public static boolean checkStuckSpeed(GrimPlayer player, double expand) {
        SimpleCollisionBox aABB = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z).expand(expand);

        Location blockPos = new Location(null, aABB.minX, aABB.minY, aABB.minZ);
        Location blockPos2 = new Location(null, aABB.maxX, aABB.maxY, aABB.maxZ);

        if (CheckIfChunksLoaded.areChunksUnloadedAt(player, blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ(), blockPos2.getBlockX(), blockPos2.getBlockY(), blockPos2.getBlockZ()))
            return false;

        for (int i = blockPos.getBlockX(); i <= blockPos2.getBlockX(); ++i) {
            for (int j = blockPos.getBlockY(); j <= blockPos2.getBlockY(); ++j) {
                for (int k = blockPos.getBlockZ(); k <= blockPos2.getBlockZ(); ++k) {
                    WrappedBlockState block = player.compensatedWorld.getBlock(i, j, k);
                    StateType blockType = block.getType();

                    if (blockType == StateTypes.COBWEB) {
                        return true;
                    }

                    if (blockType == StateTypes.SWEET_BERRY_BUSH && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)) {
                        return true;
                    }

                    if (blockType == StateTypes.POWDER_SNOW && i == Math.floor(player.x) && j == Math.floor(player.y) && k == Math.floor(player.z) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean suffocatesAt(GrimPlayer player, SimpleCollisionBox playerBB) {
        for (int y = (int) Math.floor(playerBB.minY); y < Math.ceil(playerBB.maxY); y++) {
            for (int z = (int) Math.floor(playerBB.minZ); z < Math.ceil(playerBB.maxZ); z++) {
                for (int x = (int) Math.floor(playerBB.minX); x < Math.ceil(playerBB.maxX); x++) {
                    if (doesBlockSuffocate(player, x, y, z)) {
                        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_16)) {
                            WrappedBlockState data = player.compensatedWorld.getBlock(x, y, z);
                            CollisionBox box = CollisionData.getData(data.getType()).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z);

                            if (!box.isIntersected(playerBB)) continue;
                        }

                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean doesBlockSuffocate(GrimPlayer player, int x, int y, int z) {
        WrappedBlockState data = player.compensatedWorld.getBlock(x, y, z);
        StateType mat = data.getType();

        if (!mat.isSolid()) return false;

        if (mat == StateTypes.OBSERVER || mat == StateTypes.REDSTONE_BLOCK)
            return player.getClientVersion().isNewerThan(ClientVersion.V_1_13_2);
        if (mat == StateTypes.TNT)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14);
        if (mat == StateTypes.FARMLAND)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_16);
        if (mat == StateTypes.SOUL_SAND)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_16) || player.getClientVersion().isOlderThan(ClientVersion.V_1_14);
        if ((mat == StateTypes.PISTON || mat == StateTypes.STICKY_PISTON) && player.getClientVersion().isOlderThan(ClientVersion.V_1_14))
            return false;
        if (mat == StateTypes.ICE || mat == StateTypes.FROSTED_ICE)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14);
        if (BlockTags.LEAVES.contains(mat) || BlockTags.GLASS_BLOCKS.contains(mat)) return false;
        if (mat == StateTypes.DIRT_PATH)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_16) || player.getClientVersion().isOlderThan(ClientVersion.V_1_9);
        if (mat == StateTypes.BEACON)
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14);

        if (Materials.isSolidBlockingBlacklist(mat, player.getClientVersion())) return false;

        CollisionBox box = CollisionData.getData(mat).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z);
        return box.isFullBlock();
    }

    public static boolean hasMaterial(GrimPlayer player, SimpleCollisionBox checkBox, Predicate<Pair<WrappedBlockState, Vector3i>> searchingFor) {
        int minBlockX = (int) Math.floor(checkBox.minX);
        int maxBlockX = (int) Math.floor(checkBox.maxX);
        int minBlockY = (int) Math.floor(checkBox.minY);
        int maxBlockY = (int) Math.floor(checkBox.maxY);
        int minBlockZ = (int) Math.floor(checkBox.minZ);
        int maxBlockZ = (int) Math.floor(checkBox.maxZ);

        final int minSection = player.compensatedWorld.getMinHeight() >> 4;
        final int minBlock = minSection << 4;
        final int maxBlock = player.compensatedWorld.getMaxHeight() - 1;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        int minYIterate = Math.max(minBlock, minBlockY);
        int maxYIterate = Math.min(maxBlock, maxBlockY);

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            int minZ = currChunkZ == minChunkZ ? minBlockZ & 15 : 0;
            int maxZ = currChunkZ == maxChunkZ ? maxBlockZ & 15 : 15;

            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                int minX = currChunkX == minChunkX ? minBlockX & 15 : 0;
                int maxX = currChunkX == maxChunkX ? maxBlockX & 15 : 15;

                int chunkXGlobalPos = currChunkX << 4;
                int chunkZGlobalPos = currChunkZ << 4;

                Column chunk = player.compensatedWorld.getChunk(currChunkX, currChunkZ);

                if (chunk == null) continue;
                BaseChunk[] sections = chunk.chunks();

                for (int y = minYIterate; y <= maxYIterate; ++y) {
                    BaseChunk section = sections[(y >> 4) - minSection];

                    if (section == null || (IS_FOURTEEN && section.isEmpty())) {
                        y = (y & ~(15)) + 15;
                        continue;
                    }

                    for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                        for (int currX = minX; currX <= maxX; ++currX) {
                            int x = currX | chunkXGlobalPos;
                            int z = currZ | chunkZGlobalPos;

                            WrappedBlockState data = section.get(CompensatedWorld.blockVersion, x & 0xF, y & 0xF, z & 0xF, false);

                            if (searchingFor.test(new Pair<>(data, new Vector3i(x, y, z))))
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void forEachCollisionBox(@NotNull GrimPlayer player, @NotNull SimpleCollisionBox checkBox, @NotNull BiConsumer<WrappedBlockState, Vector3i> searchingFor) {
        int minBlockX = (int) Math.floor(checkBox.minX - COLLISION_EPSILON) - 1;
        int maxBlockX = (int) Math.floor(checkBox.maxX + COLLISION_EPSILON) + 1;
        int minBlockY = (int) Math.floor(checkBox.minY - COLLISION_EPSILON) - 1;
        int maxBlockY = (int) Math.floor(checkBox.maxY + COLLISION_EPSILON) + 1;
        int minBlockZ = (int) Math.floor(checkBox.minZ - COLLISION_EPSILON) - 1;
        int maxBlockZ = (int) Math.floor(checkBox.maxZ + COLLISION_EPSILON) + 1;

        final int minSection = player.compensatedWorld.getMinHeight() >> 4;
        final int minBlock = minSection << 4;
        final int maxBlock = player.compensatedWorld.getMaxHeight() - 1;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        int minYIterate = Math.max(minBlock, minBlockY);
        int maxYIterate = Math.min(maxBlock, maxBlockY);

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            int minZ = currChunkZ == minChunkZ ? minBlockZ & 15 : 0;
            int maxZ = currChunkZ == maxChunkZ ? maxBlockZ & 15 : 15;

            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                int minX = currChunkX == minChunkX ? minBlockX & 15 : 0;
                int maxX = currChunkX == maxChunkX ? maxBlockX & 15 : 15;

                int chunkXGlobalPos = currChunkX << 4;
                int chunkZGlobalPos = currChunkZ << 4;

                Column chunk = player.compensatedWorld.getChunk(currChunkX, currChunkZ);

                if (chunk == null) continue;
                BaseChunk[] sections = chunk.chunks();

                for (int y = minYIterate; y <= maxYIterate; ++y) {
                    BaseChunk section = sections[(y >> 4) - minSection];

                    if (section == null || (IS_FOURTEEN && section.isEmpty())) {
                        y = (y & ~(15)) + 15;
                        continue;
                    }

                    for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                        for (int currX = minX; currX <= maxX; ++currX) {
                            int x = currX | chunkXGlobalPos;
                            int z = currZ | chunkZGlobalPos;

                            WrappedBlockState data = section.get(CompensatedWorld.blockVersion, x & 0xF, y & 0xF, z & 0xF, false);

                            if (data.getGlobalId() == 0) continue;

                            int edgeCount = ((x == minBlockX || x == maxBlockX) ? 1 : 0) +
                                    ((y == minBlockY || y == maxBlockY) ? 1 : 0) +
                                    ((z == minBlockZ || z == maxBlockZ) ? 1 : 0);

                            final StateType type = data.getType();
                            if (edgeCount != 3 && (edgeCount != 1 || Materials.isShapeExceedsCube(type))
                                    && (edgeCount != 2 || type == StateTypes.PISTON_HEAD)) {
                                final CollisionBox collisionBox = CollisionData.getData(type).getMovementCollisionBox(player, player.getClientVersion(), data, x, y, z);

                                if (collisionBox.isIntersected(checkBox)) {
                                    searchingFor.accept(data, new Vector3i(x, y, z));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean onClimbable(GrimPlayer player, double x, double y, double z) {
        WrappedBlockState blockState = player.compensatedWorld.getBlock(x, y, z);
        StateType blockMaterial = blockState.getType();

        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_11) &&
                player.isGliding && BlockTags.CAN_GLIDE_THROUGH.contains(blockMaterial)) {
            return false;
        }

        if (blockMaterial == StateTypes.CAVE_VINES || blockMaterial == StateTypes.CAVE_VINES_PLANT) {
            return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17);
        }

        if (player.tagManager.block(SyncedTags.CLIMBABLE).contains(blockMaterial)) {
            return true;
        }

        if (blockMaterial == StateTypes.SWEET_BERRY_BUSH && player.getClientVersion().isOlderThan(ClientVersion.V_1_14)) {
            return true;
        }

        return trapdoorUsableAsLadder(player, x, y, z, blockState);
    }

    public static boolean trapdoorUsableAsLadder(GrimPlayer player, double x, double y, double z, WrappedBlockState blockData) {
        if (!BlockTags.TRAPDOORS.contains(blockData.getType())) return false;
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) return false;

        if (blockData.isOpen()) {
            WrappedBlockState blockBelow = player.compensatedWorld.getBlock(x, y - 1, z);

            if (blockBelow.getType() == StateTypes.LADDER) {
                return blockData.getFacing() == blockBelow.getFacing();
            }
        }

        return false;
    }

    public enum Axis {
        X {
            @Override
            public double get(Vector3d vector) {
                return vector.getX();
            }

            @Override
            public int get(Vector3i vector) {
                return vector.getX();
            }

            @Override
            public double choose(double x, double y, double z) {
                return x;
            }

            @Override
            public int choose(int x, int y, int z) {
                return x;
            }

            @Override
            public Direction getPositive() {
                return Direction.EAST;
            }

            @Override
            public Direction getNegative() {
                return Direction.WEST;
            }
        },
        Y {
            @Override
            public double get(Vector3d vector) {
                return vector.getY();
            }

            @Override
            public int get(Vector3i vector) {
                return vector.getY();
            }

            @Override
            public double choose(double x, double y, double z) {
                return y;
            }

            @Override
            public int choose(int x, int y, int z) {
                return y;
            }

            @Override
            public Direction getPositive() {
                return Direction.UP;
            }

            @Override
            public Direction getNegative() {
                return Direction.DOWN;
            }
        },
        Z {
            @Override
            public double get(Vector3d vector) {
                return vector.getZ();
            }

            @Override
            public int get(Vector3i vector) {
                return vector.getZ();
            }

            @Override
            public double choose(double x, double y, double z) {
                return z;
            }

            @Override
            public int choose(int x, int y, int z) {
                return z;
            }

            @Override
            public Direction getPositive() {
                return Direction.SOUTH;
            }

            @Override
            public Direction getNegative() {
                return Direction.NORTH;
            }
        };

        public abstract double get(Vector3d vector);

        public abstract int get(Vector3i vector);

        public abstract double choose(double x, double y, double z);

        public abstract int choose(int x, int y, int z);

        public abstract Direction getPositive();

        public abstract Direction getNegative();
    }
}
