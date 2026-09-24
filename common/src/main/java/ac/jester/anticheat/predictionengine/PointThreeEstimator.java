package ac.jester.anticheat.predictionengine;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngine;
import ac.jester.anticheat.utils.collisions.CollisionData;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.VelocityData;
import ac.jester.anticheat.utils.data.tags.SyncedTags;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.FluidTypeFlowing;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import lombok.Getter;
import lombok.Setter;

import java.util.OptionalInt;
import java.util.Set;

public class PointThreeEstimator {
    private final GrimPlayer player;
    public boolean isNearFluid = false;
    private boolean headHitter = false;
    @Getter
    private boolean isNearClimbable = false;
    private boolean isGliding = false;
    private boolean gravityChanged = false;

    private boolean isNearHorizontalFlowingLiquid = false;
    private boolean isNearVerticalFlowingLiquid = false;
    private boolean isNearBubbleColumn = false;

    private int maxPositiveLevitation = Integer.MIN_VALUE;
    private int minNegativeLevitation = Integer.MAX_VALUE;

    @Setter
    @Getter
    private boolean isPushing = false;

    @Getter
    private boolean wasAlwaysCertain = true;

    public PointThreeEstimator(GrimPlayer player) {
        this.player = player;
    }

    public void handleChangeBlock(int x, int y, int z, WrappedBlockState state) {
        final StateType stateType = state.getType();
        CollisionBox data = CollisionData.getData(stateType).getMovementCollisionBox(player, player.getClientVersion(), state, x, y, z);
        SimpleCollisionBox normalBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.x, player.y, player.z, 0.6f, 1.8f);

        final double movementThreshold = player.getMovementThreshold();
        SimpleCollisionBox slightlyExpanded = normalBox.copy().expand(movementThreshold, 0, movementThreshold);
        if (!slightlyExpanded.isIntersected(data) && slightlyExpanded.offset(0, movementThreshold, 0).isIntersected(data)) {
            headHitter = true;
        }

        final float collisionBoxThreshold = (float) (movementThreshold * 2.f);
        SimpleCollisionBox pointThreeBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.x, player.y - movementThreshold, player.z, 0.6f + collisionBoxThreshold, 1.8f + collisionBoxThreshold);
        if ((Materials.isWater(player.getClientVersion(), state) || stateType == StateTypes.LAVA) &&
                pointThreeBox.isIntersected(new SimpleCollisionBox(x, y, z))) {
            if (stateType == StateTypes.BUBBLE_COLUMN && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
                isNearBubbleColumn = true;
            }

            Vector3dm fluidVector = FluidTypeFlowing.getFlow(player, x, y, z);
            if (fluidVector.getX() != 0 || fluidVector.getZ() != 0) {
                isNearHorizontalFlowingLiquid = true;
            }
            if (fluidVector.getY() != 0) {
                isNearVerticalFlowingLiquid = true;
            }

            isNearFluid = true;
        }

        if (pointThreeBox.isIntersected(new SimpleCollisionBox(x, y, z))) {
            int controllingEntityId = player.inVehicle() ? player.getRidingVehicleId() : player.entityID;

            final VelocityData oldFirstBreadKB = player.firstBreadKB;
            final VelocityData oldLikelyKB = player.likelyKB;
            player.firstBreadKB = player.checkManager.getKnockbackHandler().calculateFirstBreadKnockback(controllingEntityId, player.lastTransactionReceived.get());
            player.likelyKB = player.checkManager.getKnockbackHandler().calculateRequiredKB(controllingEntityId, player.lastTransactionReceived.get(), true);

            final VelocityData oldFirstBreadEx = player.firstBreadExplosion;
            final VelocityData oldLikelyEx = player.likelyExplosions;
            player.firstBreadExplosion = player.checkManager.getExplosionHandler().getFirstBreadAddedExplosion(player.lastTransactionReceived.get());
            player.likelyExplosions = player.checkManager.getExplosionHandler().getPossibleExplosions(player.lastTransactionReceived.get(), true);

            player.updateVelocityMovementSkipping();

            if (player.couldSkipTick) {
                player.uncertaintyHandler.lastPointThree.reset();
            } else {
                player.firstBreadKB = oldFirstBreadKB;
                player.likelyKB = oldLikelyKB;
                player.firstBreadExplosion = oldFirstBreadEx;
                player.likelyExplosions = oldLikelyEx;
            }
        }

        if (!player.inVehicle() && ((stateType == StateTypes.POWDER_SNOW && player.inventory.getBoots().getType() == ItemTypes.LEATHER_BOOTS)
                || player.tagManager.block(SyncedTags.CLIMBABLE).contains(stateType)) && pointThreeBox.isIntersected(new SimpleCollisionBox(x, y, z))) {
            isNearClimbable = true;
        }
    }

    public boolean canPredictNextVerticalMovement() {
        return !gravityChanged && maxPositiveLevitation == Integer.MIN_VALUE && minNegativeLevitation == Integer.MAX_VALUE;
    }

    public double positiveLevitation(double y) {
        if (maxPositiveLevitation == Integer.MIN_VALUE) return y;
        return (0.05 * (maxPositiveLevitation + 1) - y * 0.2);
    }

    public double negativeLevitation(double y) {
        if (minNegativeLevitation == Integer.MAX_VALUE) return y;
        return (0.05 * (minNegativeLevitation + 1) - y * 0.2);
    }

    public boolean controlsVerticalMovement() {
        return isNearFluid || isNearClimbable || isNearHorizontalFlowingLiquid || isNearVerticalFlowingLiquid || isNearBubbleColumn || isGliding || player.uncertaintyHandler.influencedByBouncyBlock()
                || player.checkManager.getKnockbackHandler().isKnockbackPointThree() || player.checkManager.getExplosionHandler().isExplosionPointThree();
    }

    public void updatePlayerPotions(PotionType potion, Integer level) {
        if (potion == PotionTypes.LEVITATION) {
            maxPositiveLevitation = Math.max(level == null ? Integer.MIN_VALUE : level, maxPositiveLevitation);
            minNegativeLevitation = Math.min(level == null ? Integer.MAX_VALUE : level, minNegativeLevitation);
        }
    }

    public void updatePlayerGliding() {
        isGliding = true;
    }

    public void updatePlayerGravity() {
        gravityChanged = true;
    }

    public void endOfTickTick() {
        double movementThreshold = player.getMovementThreshold();
        float collisionBoxThreshold = (float) (movementThreshold * 2.f);
        SimpleCollisionBox pointThreeBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.x, player.y - movementThreshold, player.z, 0.6f + collisionBoxThreshold, 1.8f + collisionBoxThreshold);

        SimpleCollisionBox oldBB = player.boundingBox;

        headHitter = false;
        for (float sizes : (player.skippedTickInActualMovement ? new float[]{0.6f, 1.5f, 1.8f} : new float[]{player.pose.height})) {
            player.boundingBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.x, player.y + (sizes - 0.01f), player.z, 0.6f, 0.01f);
            headHitter = headHitter || Collisions.collide(player, 0, movementThreshold, 0).getY() != movementThreshold;
        }

        player.boundingBox = oldBB;

        checkNearbyBlocks(pointThreeBox);

        maxPositiveLevitation = Integer.MIN_VALUE;
        minNegativeLevitation = Integer.MAX_VALUE;

        isGliding = player.isGliding;
        gravityChanged = false;
        wasAlwaysCertain = true;
        isPushing = false;
    }

    private void checkNearbyBlocks(SimpleCollisionBox pointThreeBox) {
        isNearHorizontalFlowingLiquid = false;
        isNearVerticalFlowingLiquid = false;
        isNearClimbable = false;
        isNearBubbleColumn = false;
        isNearFluid = false;

        Collisions.hasMaterial(player, pointThreeBox, (pair) -> {
            final WrappedBlockState state = pair.first();
            final StateType stateType = state.getType();
            final Vector3i pos = pair.second();
            if (player.tagManager.block(SyncedTags.CLIMBABLE).contains(stateType) || (stateType == StateTypes.POWDER_SNOW && !player.inVehicle() && player.inventory.getBoots().getType() == ItemTypes.LEATHER_BOOTS)) {
                isNearClimbable = true;
            }

            if (BlockTags.TRAPDOORS.contains(stateType)) {
                isNearClimbable = isNearClimbable || Collisions.trapdoorUsableAsLadder(player, pos.getX(), pos.getY(), pos.getZ(), state);
            }

            if (stateType == StateTypes.BUBBLE_COLUMN && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
                isNearBubbleColumn = true;
            }

            if (Materials.isWater(player.getClientVersion(), pair.first()) || pair.first().getType() == StateTypes.LAVA) {
                isNearFluid = true;
            }

            Vector3dm fluidVector = FluidTypeFlowing.getFlow(player, pos.getX(), pos.getY(), pos.getZ());
            if (fluidVector.getX() != 0 || fluidVector.getZ() != 0) {
                isNearHorizontalFlowingLiquid = true;
            }

            if (fluidVector.getY() != 0) {
                isNearVerticalFlowingLiquid = true;
            }

            return false;
        });
    }

    public boolean closeEnoughToGroundToStepWithPointThree(VectorData data, double originalY) {
        if (player.inVehicle()) return false;
        if (!player.isPointThree()) return false;

        if (player.clientControlledVerticalCollision && data != null && data.isZeroPointZeroThree()) {
            return checkForGround(originalY);
        }

        return false;
    }

    private boolean checkForGround(double y) {
        SimpleCollisionBox playerBox = player.boundingBox;
        double threshold = player.getMovementThreshold();
        player.boundingBox = player.boundingBox.copy().expand(threshold, 0, threshold).offset(0, threshold, 0);
        double searchDistance = -0.2 + Math.min(0, y);
        Vector3dm collisionResult = Collisions.collide(player, 0, searchDistance, 0);
        player.boundingBox = playerBox;
        return collisionResult.getY() != searchDistance;
    }

    public boolean determineCanSkipTick(float speed, Set<VectorData> init) {
        if (!player.canSkipTicks() && player.packetStateData.didLastMovementIncludePosition && !player.uncertaintyHandler.isSteppingOnSlime) {
            return false;
        }

        double minimum = Double.MAX_VALUE;

        if ((player.isGliding || player.wasGliding) && !player.packetStateData.didLastMovementIncludePosition) {
            return true;
        }

        if (player.inVehicle()) {
            return false;
        }

        if (isNearClimbable() || isPushing || player.uncertaintyHandler.wasAffectedByStuckSpeed() || player.fireworks.getMaxFireworksAppliedPossible() > 0) {
            return true;
        }

        boolean couldStep = player.isPointThree() && checkForGround(player.clientVelocity.getY());

        for (VectorData data : init) {
            Vector3dm toZeroVec = new PredictionEngine().handleStartingVelocityUncertainty(player, data, new Vector3dm());
            Vector3dm collisionResult = Collisions.collide(player, toZeroVec.getX(), toZeroVec.getY(), toZeroVec.getZ(), Integer.MIN_VALUE, null);

            boolean likelyStepSkip = player.isPointThree() && (data.vector.getY() > -0.08 && data.vector.getY() < 0.06) && couldStep;

            double minHorizLength = Math.max(0, Math.hypot(collisionResult.getX(), collisionResult.getZ()) - speed);
            boolean forcedNo003 = data.isExplosion() || data.isKnockback();
            double length = Math.hypot((!forcedNo003 && player.lastOnGround) || (likelyStepSkip || controlsVerticalMovement()) ? 0 : Math.abs(collisionResult.getY()), minHorizLength);

            minimum = Math.min(minimum, length);

            if (minimum < player.getMovementThreshold()) break;
        }

        return minimum < player.getMovementThreshold();
    }

    public double getHorizontalFluidPushingUncertainty(VectorData vector) {
        return isNearHorizontalFlowingLiquid && vector.isZeroPointZeroThree() ? 0.014 * 2 : 0;
    }

    public double getVerticalFluidPushingUncertainty(VectorData vector) {
        return (isNearBubbleColumn || isNearVerticalFlowingLiquid) && vector.isZeroPointZeroThree() ? 0.014 * 2 : 0;
    }

    public double getVerticalBubbleUncertainty(VectorData vectorData) {
        return isNearBubbleColumn && vectorData.isZeroPointZeroThree() ? 0.35 : 0;
    }

    public double getAdditionalVerticalUncertainty(VectorData vector) {
        double fluidAddition = vector.isZeroPointZeroThree() ? 0.014 : 0;

        if (player.inVehicle()) return 0;

        if (headHitter) {
            wasAlwaysCertain = false;
            return -Math.max(0, vector.vector.getY()) - 0.1 - fluidAddition;
        } else if (player.uncertaintyHandler.wasAffectedByStuckSpeed()) {
            wasAlwaysCertain = false;
            return -0.1 - fluidAddition;
        }

        if (!vector.isZeroPointZeroThree()) return 0;

        double minMovement = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) ? 0.003 : 0.005;

        double yVel = vector.vector.getY();
        double maxYTraveled = 0;
        boolean first = true;
        do {
            if (Math.abs(yVel) < minMovement) yVel = 0;

            if (!first) {
                maxYTraveled += yVel;
            }

            if (!first && yVel == 0) {
                break;
            }

            first = false;

            yVel = iterateGravity(player, yVel);

            if (yVel == 0) break;
        } while (Math.abs(maxYTraveled + vector.vector.getY()) < player.getMovementThreshold());

        if (maxYTraveled != 0) {
            wasAlwaysCertain = false;
        }

        return maxYTraveled;
    }

    private double iterateGravity(GrimPlayer player, double y) {
        final OptionalInt levitation = player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.LEVITATION);
        if (levitation.isPresent()) {
            y += (0.05 * (levitation.getAsInt() + 1) - y) * 0.2;
        } else if (player.hasGravity) {
            y -= player.gravity;
        }

        return y * 0.98;
    }
}
