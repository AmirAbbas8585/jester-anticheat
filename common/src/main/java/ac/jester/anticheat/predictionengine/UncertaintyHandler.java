package ac.jester.anticheat.predictionengine;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.LastInstance;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityRideable;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityStrider;
import ac.jester.anticheat.utils.lists.EvictingQueue;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.BoundingBoxSize;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.BlockFace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class UncertaintyHandler {
    private final GrimPlayer player;
    public final EvictingQueue<Double> pistonX = new EvictingQueue<>(5);
    public final EvictingQueue<Double> pistonY = new EvictingQueue<>(5);
    public final EvictingQueue<Double> pistonZ = new EvictingQueue<>(5);
    public boolean isStepMovement;
    public HashSet<BlockFace> slimePistonBounces;
    public double xNegativeUncertainty = 0;
    public double xPositiveUncertainty = 0;
    public double zNegativeUncertainty = 0;
    public double zPositiveUncertainty = 0;
    public double yNegativeUncertainty = 0;
    public double yPositiveUncertainty = 0;
    public double thisTickSlimeBlockUncertainty = 0;
    public double nextTickSlimeBlockUncertainty = 0;
    public boolean onGroundUncertain = false;
    public boolean lastPacketWasGroundPacket = false;
    public boolean isSteppingOnSlime = false;
    public boolean isSteppingOnIce = false;
    public boolean isSteppingOnHoney = false;
    public boolean wasSteppingOnBouncyBlock = false;
    public boolean isSteppingOnBouncyBlock = false;
    public boolean isSteppingNearBubbleColumn = false;
    public boolean isSteppingNearScaffolding = false;
    public boolean isSteppingNearShulker = false;
    public boolean isNearGlitchyBlock = false;
    public boolean isOrWasNearGlitchyBlock = false;
    public boolean claimingLeftStuckSpeed = false;
    public boolean lastMovementWasZeroPointZeroThree = false;
    public boolean lastMovementWasUnknown003VectorReset = false;
    public boolean wasZeroPointThreeVertically = false;
    public final EvictingQueue<Integer> collidingEntities = new EvictingQueue<>(3);
    public final EvictingQueue<Integer> riptideEntities = new EvictingQueue<>(3);
    public final List<Integer> fishingRodPulls = new ArrayList<>();
    public SimpleCollisionBox fireworksBox = null;
    public SimpleCollisionBox fishingRodPullBox = null;

    public final LastInstance lastFlyingTicks;
    public final LastInstance lastFlyingStatusChange;
    public final LastInstance lastUnderwaterFlyingHack;
    public final LastInstance lastStuckSpeedMultiplier;
    public final LastInstance lastHardCollidingLerpingEntity;
    public final LastInstance lastThirtyMillionHardBorder;
    public final LastInstance lastTeleportTicks;
    public final LastInstance lastPointThree;
    public final LastInstance stuckOnEdge;
    public final LastInstance lastStuckNorth;
    public final LastInstance lastStuckSouth;
    public final LastInstance lastStuckWest;
    public final LastInstance lastStuckEast;
    public final LastInstance lastVehicleSwitch;
    public double lastHorizontalOffset = 0;
    public double lastVerticalOffset = 0;

    public UncertaintyHandler(GrimPlayer player) {
        this.player = player;
        this.lastFlyingTicks = new LastInstance(player);
        this.lastFlyingStatusChange = new LastInstance(player);
        this.lastUnderwaterFlyingHack = new LastInstance(player);
        this.lastStuckSpeedMultiplier = new LastInstance(player);
        this.lastHardCollidingLerpingEntity = new LastInstance(player);
        this.lastThirtyMillionHardBorder = new LastInstance(player);
        this.lastTeleportTicks = new LastInstance(player);
        this.lastPointThree = new LastInstance(player);
        this.stuckOnEdge = new LastInstance(player);
        this.lastStuckNorth = new LastInstance(player);
        this.lastStuckSouth = new LastInstance(player);
        this.lastStuckWest = new LastInstance(player);
        this.lastStuckEast = new LastInstance(player);
        this.lastVehicleSwitch = new LastInstance(player);
        tick();

        this.riptideEntities.add(0);
        this.collidingEntities.add(0);
    }

    public void tick() {
        pistonX.add(0d);
        pistonY.add(0d);
        pistonZ.add(0d);
        isStepMovement = false;

        isSteppingNearShulker = false;
        wasSteppingOnBouncyBlock = isSteppingOnBouncyBlock;
        isSteppingOnSlime = false;
        isSteppingOnBouncyBlock = false;
        isSteppingOnIce = false;
        isSteppingOnHoney = false;
        isSteppingNearBubbleColumn = false;
        isSteppingNearScaffolding = false;

        slimePistonBounces = new HashSet<>();
        tickFireworksBox();
    }

    public boolean wasAffectedByStuckSpeed() {
        return lastStuckSpeedMultiplier.hasOccurredSince(5);
    }

    public void tickFireworksBox() {
        fishingRodPullBox = fishingRodPulls.isEmpty() ? null : new SimpleCollisionBox();
        fireworksBox = null;

        for (int owner : fishingRodPulls) {
            PacketEntity entity = player.compensatedEntities.getEntity(owner);
            if (entity == null) continue;

            SimpleCollisionBox entityBox = entity.getPossibleCollisionBoxes();
            final float scale = (float) entity.getAttributeValue(Attributes.SCALE);
            float width = BoundingBoxSize.getWidth(player, entity) * scale;
            float height = BoundingBoxSize.getHeight(player, entity) * scale;

            entityBox.maxY -= height;
            entityBox.expand(-width / 2, 0, -width / 2);

            Vector3dm maxLocation = new Vector3dm(entityBox.maxX, entityBox.maxY, entityBox.maxZ);
            Vector3dm minLocation = new Vector3dm(entityBox.minX, entityBox.minY, entityBox.minZ);

            Vector3dm diff = minLocation.subtract(player.lastX, player.lastY + 0.8 * 1.8, player.lastZ).multiply(0.1);
            fishingRodPullBox.minX = Math.min(0, diff.getX());
            fishingRodPullBox.minY = Math.min(0, diff.getY());
            fishingRodPullBox.minZ = Math.min(0, diff.getZ());

            diff = maxLocation.subtract(player.lastX, player.lastY + 0.8 * 1.8, player.lastZ).multiply(0.1);
            fishingRodPullBox.maxX = Math.max(0, diff.getX());
            fishingRodPullBox.maxY = Math.max(0, diff.getY());
            fishingRodPullBox.maxZ = Math.max(0, diff.getZ());
        }

        fishingRodPulls.clear();

        int maxFireworks = player.fireworks.getMaxFireworksAppliedPossible() * 2;
        if (maxFireworks <= 0 || (!player.isGliding && !player.wasGliding)) {
            return;
        }

        fireworksBox = new SimpleCollisionBox();

        Vector3dm currentLook = ReachUtils.getLook(player, player.yaw, player.pitch);
        Vector3dm lastLook = ReachUtils.getLook(player, player.lastYaw, player.lastPitch);

        double antiTickSkipping = player.isPointThree() ? 0 : 0.05;

        double minX = Math.min(-antiTickSkipping, currentLook.getX()) + Math.min(-antiTickSkipping, lastLook.getX());
        double minY = Math.min(-antiTickSkipping, currentLook.getY()) + Math.min(-antiTickSkipping, lastLook.getY());
        double minZ = Math.min(-antiTickSkipping, currentLook.getZ()) + Math.min(-antiTickSkipping, lastLook.getZ());
        double maxX = Math.max(antiTickSkipping, currentLook.getX()) + Math.max(antiTickSkipping, lastLook.getX());
        double maxY = Math.max(antiTickSkipping, currentLook.getY()) + Math.max(antiTickSkipping, lastLook.getY());
        double maxZ = Math.max(antiTickSkipping, currentLook.getZ()) + Math.max(antiTickSkipping, lastLook.getZ());

        minX *= 1.7;
        minY *= 1.7;
        minZ *= 1.7;
        maxX *= 1.7;
        maxY *= 1.7;
        maxZ *= 1.7;

        minX = Math.max(-1.7, minX);
        minY = Math.max(-1.7, minY);
        minZ = Math.max(-1.7, minZ);
        maxX = Math.min(1.7, maxX);
        maxY = Math.min(1.7, maxY);
        maxZ = Math.min(1.7, maxZ);

        fireworksBox = new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public double getOffsetHorizontal(VectorData data) {
        double threshold = player.getMovementThreshold();

        boolean newVectorPointThree = player.couldSkipTick && data.isKnockback() && !data.isSetbackKb(player);
        boolean explicit003 = data.isZeroPointZeroThree() || lastMovementWasZeroPointZeroThree;
        boolean either003 = newVectorPointThree || explicit003;

        double pointThree = newVectorPointThree || lastMovementWasUnknown003VectorReset ? threshold : 0;

        if (explicit003) {
            pointThree = 0.91 * 0.6 * (threshold * 2) + threshold;
        }

        if (either003 && (influencedByBouncyBlock() || isSteppingOnHoney))
            pointThree = 0.91 * 0.8 * (threshold * 2) + threshold;

        if (either003 && isSteppingOnIce)
            pointThree = 0.91 * 0.989 * (threshold * 2) + threshold;

        if (pointThree > threshold)
            pointThree *= 0.91 * 0.989;

        if (either003 && (player.lastOnGround || player.isFlying))
            pointThree = 0.91 * (threshold * 2) + threshold;

        if (either003 && (player.isGliding || player.wasGliding)) {
            pointThree = (0.99 * (threshold * 2)) + threshold;
        }

        if (player.uncertaintyHandler.claimingLeftStuckSpeed)
            pointThree = 0.15;

        return pointThree;
    }

    public boolean influencedByBouncyBlock() {
        return isSteppingOnBouncyBlock || wasSteppingOnBouncyBlock;
    }

    public double getVerticalOffset(VectorData data) {
        if (player.uncertaintyHandler.claimingLeftStuckSpeed)
            return 0.06;

        if (player.uncertaintyHandler.wasSteppingOnBouncyBlock && (player.wasTouchingWater || player.wasTouchingLava))
            return 0.06;

        if ((lastFlyingTicks.hasOccurredSince(5)) && Math.abs(data.vector.getY()) < (4.5 * player.flySpeed - 0.25))
            return 0.06;

        double pointThree = player.getMovementThreshold();
        if (data.isTrident())
            return pointThree * 2;

        if (player.couldSkipTick && (data.isKnockback() || player.isClimbing) && !data.isZeroPointZeroThree())
            return pointThree;

        if (player.pointThreeEstimator.controlsVerticalMovement()) {
            if (data.isZeroPointZeroThree() || lastMovementWasZeroPointZeroThree)
                return pointThree * 2;
        }

        if (wasZeroPointThreeVertically || player.uncertaintyHandler.onGroundUncertain || player.uncertaintyHandler.lastPacketWasGroundPacket)
            return pointThree;

        return 0;
    }

    public double reduceOffset(double offset) {
        if (player.uncertaintyHandler.lastHardCollidingLerpingEntity.hasOccurredSince(3)) {
            offset -= 1.2;
        }

        if (player.uncertaintyHandler.isOrWasNearGlitchyBlock) {
            offset -= 0.25;
        }

        if (player.uncertaintyHandler.wasAffectedByStuckSpeed() && (!player.isPointThree() || player.inVehicle())) {
            offset -= 0.01;
        }

        if (player.uncertaintyHandler.influencedByBouncyBlock() && (!player.isPointThree() || player.inVehicle())) {
            offset -= 0.03;
        }

        if (player.compensatedEntities.self.getRiding() instanceof PacketEntityRideable vehicle) {
            if (vehicle.currentBoostTime < vehicle.boostTimeMax + 20)
                offset -= 0.01;
        }

        return Math.max(0, offset);
    }

    public void checkForHardCollision() {
        if (hasHardCollision()) player.uncertaintyHandler.lastHardCollidingLerpingEntity.reset();
    }

    private boolean hasHardCollision() {
        SimpleCollisionBox expandedBB = player.boundingBox.copy().expand(1);
        return isSteppingNearShulker || regularHardCollision(expandedBB) || striderCollision(expandedBB) || boatCollision(expandedBB);
    }

    private boolean regularHardCollision(SimpleCollisionBox expandedBB) {
        final PacketEntity riding = player.compensatedEntities.self.getRiding();
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if ((entity.isBoat || entity.type == EntityTypes.SHULKER || entity.isHappyGhast) && entity != riding
                    && entity.getPossibleCollisionBoxes().isIntersected(expandedBB)) {
                return true;
            }
        }

        return false;
    }

    private boolean striderCollision(SimpleCollisionBox expandedBB) {
        if (player.compensatedEntities.self.getRiding() instanceof PacketEntityStrider) {
            for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
                if (entity.type == EntityTypes.STRIDER && entity != player.compensatedEntities.self.getRiding()
                        && !entity.hasPassenger(entity) && entity.getPossibleCollisionBoxes().isIntersected(expandedBB)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean boatCollision(SimpleCollisionBox expandedBB) {
        final PacketEntity riding = player.compensatedEntities.self.getRiding();
        if (riding == null || !riding.isBoat) return false;

        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity != riding && entity.isPushable() && !riding.hasPassenger(entity)
                    && entity.getPossibleCollisionBoxes().isIntersected(expandedBB)) {
                return true;
            }
        }
        return false;
    }
}
