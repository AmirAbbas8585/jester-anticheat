package ac.jester.anticheat.checks.impl.breaking;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.math.VectorUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;

@CheckData(name = "FarBreak", description = "Breaking blocks too far away", experimental = true)
public class FarBreak extends Check implements BlockBreakCheck {
    public FarBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (!player.cameraEntity.isSelf() || player.inVehicle() || blockBreak.action == DiggingAction.CANCELLED_DIGGING)
            return;

        double min = Double.MAX_VALUE;
        for (double d : player.getPossibleEyeHeights()) {
            SimpleCollisionBox box = new SimpleCollisionBox(blockBreak.position);
            Vector3dm best = VectorUtils.cutBoxToVector(player.x, player.y + d, player.z, box);
            min = Math.min(min, best.distanceSquared(player.x, player.y + d, player.z));
        }

        double maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (player.packetStateData.didLastMovementIncludePosition || player.canSkipTicks()) {
            double threshold = player.getMovementThreshold();
            maxReach += Math.hypot(threshold, threshold);
        }
        maxReach += 0.4;

        if (min > maxReach * maxReach && flagAndAlert(String.format("distance=%.2f max=%.2f", Math.sqrt(min), maxReach)) && shouldModifyPackets()) {
            blockBreak.cancel();
        }
    }
}
