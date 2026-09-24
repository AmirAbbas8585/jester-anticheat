package ac.jester.anticheat.predictionengine.predictions;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.enums.FluidTag;
import ac.jester.anticheat.utils.math.GrimMath;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.FluidFallingAdjustedMovement;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.HashSet;
import java.util.Set;

public class PredictionEngineWater extends PredictionEngine {
    private boolean isFalling;
    private double playerGravity;
    private float swimmingFriction;

    public static void staticVectorEndOfTick(GrimPlayer player, Vector3dm vector, float swimmingFriction, double playerGravity, boolean isFalling) {
        vector.multiply(swimmingFriction, 0.8F, swimmingFriction);
        Vector3dm fluidVector = FluidFallingAdjustedMovement.getFluidFallingAdjustedMovement(player, playerGravity, isFalling, vector);
        vector.setX(fluidVector.getX());
        vector.setY(fluidVector.getY());
        vector.setZ(fluidVector.getZ());
    }

    public static Set<VectorData> transformSwimmingVectors(GrimPlayer player, Set<VectorData> base) {
        Set<VectorData> swimmingVelocities = new HashSet<>();

        if ((player.wasEyeInWater || player.fluidOnEyes == FluidTag.WATER || player.isSwimming || player.wasSwimming) && !player.inVehicle()) {
            for (VectorData vector : base) {
                double lookYAmount = ReachUtils.getLook(player, player.yaw, player.pitch).getY();
                double scalar = lookYAmount < -0.2 ? 0.085 : 0.06;

                swimmingVelocities.add(vector.returnNewModified(new Vector3dm(vector.vector.getX(), vector.vector.getY() + ((lookYAmount - vector.vector.getY()) * scalar), vector.vector.getZ()), VectorData.VectorType.SwimmingSpace));

                swimmingVelocities.add(vector.returnNewModified(vector.vector, VectorData.VectorType.SurfaceSwimming));
            }
            return swimmingVelocities;
        }
        return base;
    }

    public void guessBestMovement(float swimmingSpeed, GrimPlayer player, boolean isFalling, double playerGravity, float swimmingFriction) {
        this.isFalling = isFalling;
        this.playerGravity = playerGravity;
        this.swimmingFriction = swimmingFriction;
        super.guessBestMovement(swimmingSpeed, player);
    }

    @Override
    public void addJumpsToPossibilities(GrimPlayer player, Set<VectorData> existingVelocities) {
        for (VectorData vector : new HashSet<>(existingVelocities)) {
            if (player.couldSkipTick && vector.isZeroPointZeroThree()) {
                double extraVelFromVertTickSkipUpwards = GrimMath.clamp(player.actualMovement.getY(), vector.vector.clone().getY(), vector.vector.clone().getY() + 0.05f);
                existingVelocities.add(new VectorData(vector.vector.clone().setY(extraVelFromVertTickSkipUpwards), vector, VectorData.VectorType.Jump));
            } else {
                existingVelocities.add(new VectorData(vector.vector.clone().add(0, 0.04f, 0), vector, VectorData.VectorType.Jump));
            }

            if (player.slightlyTouchingWater && player.lastOnGround && !player.onGround) {
                Vector3dm withJump = vector.vector.clone();
                super.doJump(player, withJump);
                existingVelocities.add(new VectorData(withJump, vector, VectorData.VectorType.Jump));
            }
        }
    }

    @Override
    public void endOfTick(GrimPlayer player, double playerGravity) {
        super.endOfTick(player, playerGravity);

        for (VectorData vector : player.getPossibleVelocitiesMinusKnockback()) {
            staticVectorEndOfTick(player, vector.vector, swimmingFriction, playerGravity, isFalling);
        }
    }

    @Override
    public Set<VectorData> fetchPossibleStartTickVectors(GrimPlayer player) {
        if (player.lastWasClimbing == 0 && player.pointThreeEstimator.isNearClimbable() && (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14) || !Collisions.isEmpty(player, player.boundingBox.copy().expand(
                player.clientVelocity.getX(), 0, player.clientVelocity.getZ()).expand(0.5, -SimpleCollisionBox.COLLISION_EPSILON, 0.5)))) {
            player.lastWasClimbing = FluidFallingAdjustedMovement.getFluidFallingAdjustedMovement(player, playerGravity, isFalling, player.clientVelocity.clone().setY(0.2D * 0.8F)).getY();
        }

        Set<VectorData> baseVelocities = super.fetchPossibleStartTickVectors(player);

        return transformSwimmingVectors(player, baseVelocities);
    }
}
