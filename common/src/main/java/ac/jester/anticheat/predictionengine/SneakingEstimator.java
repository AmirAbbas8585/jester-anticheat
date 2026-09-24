package ac.jester.anticheat.predictionengine;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.VectorData;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class SneakingEstimator extends Check implements PostPredictionCheck {
    @Getter
    private SimpleCollisionBox sneakingPotentialHiddenVelocity = new SimpleCollisionBox();
    private List<VectorData> possible = new ArrayList<>();

    public SneakingEstimator(GrimPlayer player) {
        super(player);
    }

    public void storePossibleVelocities(List<VectorData> possible) {
        this.possible = possible;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        double trueFriction = player.lastOnGround ? player.friction * 0.91 : 0.91;
        if (player.wasTouchingLava) trueFriction = 0.5;
        if (player.wasTouchingWater) trueFriction = 0.96;
        if (player.isGliding) trueFriction = 0.99;

        if (!player.uncertaintyHandler.stuckOnEdge.hasOccurredSince(0)) {
            sneakingPotentialHiddenVelocity = new SimpleCollisionBox();
            return;
        }

        for (VectorData data : possible) {
            if (data.isJump() == player.predictedVelocity.isJump() && data.isKnockback() == player.predictedVelocity.isKnockback() && data.isExplosion() == player.predictedVelocity.isExplosion()) {
                if (player.uncertaintyHandler.lastStuckWest.hasOccurredSince(0) || player.uncertaintyHandler.lastStuckNorth.hasOccurredSince(0)) {
                    sneakingPotentialHiddenVelocity.minX = Math.min(sneakingPotentialHiddenVelocity.minX, data.vector.getX());
                    sneakingPotentialHiddenVelocity.minZ = Math.min(sneakingPotentialHiddenVelocity.minZ, data.vector.getZ());
                }

                if (player.uncertaintyHandler.lastStuckEast.hasOccurredSince(0) || player.uncertaintyHandler.lastStuckSouth.hasOccurredSince(0)) {
                    sneakingPotentialHiddenVelocity.maxX = Math.max(sneakingPotentialHiddenVelocity.maxX, data.vector.getX());
                    sneakingPotentialHiddenVelocity.maxZ = Math.max(sneakingPotentialHiddenVelocity.maxZ, data.vector.getZ());
                }
            }
        }

        sneakingPotentialHiddenVelocity.minX *= trueFriction;
        sneakingPotentialHiddenVelocity.minZ *= trueFriction;
        sneakingPotentialHiddenVelocity.maxX *= trueFriction;
        sneakingPotentialHiddenVelocity.maxZ *= trueFriction;

        sneakingPotentialHiddenVelocity.minX = Math.min(-0.15, sneakingPotentialHiddenVelocity.minX);
        sneakingPotentialHiddenVelocity.minZ = Math.min(-0.15, sneakingPotentialHiddenVelocity.minZ);
        sneakingPotentialHiddenVelocity.maxX = Math.max(0.15, sneakingPotentialHiddenVelocity.maxX);
        sneakingPotentialHiddenVelocity.maxZ = Math.max(0.15, sneakingPotentialHiddenVelocity.maxZ);

        if (!player.uncertaintyHandler.lastStuckEast.hasOccurredSince(0)) {
            sneakingPotentialHiddenVelocity.maxX = 0;
        }
        if (!player.uncertaintyHandler.lastStuckWest.hasOccurredSince(0)) {
            sneakingPotentialHiddenVelocity.minX = 0;
        }
        if (!player.uncertaintyHandler.lastStuckNorth.hasOccurredSince(0)) {
            sneakingPotentialHiddenVelocity.minZ = 0;
        }
        if (!player.uncertaintyHandler.lastStuckSouth.hasOccurredSince(0)) {
            sneakingPotentialHiddenVelocity.maxZ = 0;
        }
    }
}
