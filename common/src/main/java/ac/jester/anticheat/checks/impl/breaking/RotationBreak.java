package ac.jester.anticheat.checks.impl.breaking;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Ray;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationBreak", experimental = true)
public class RotationBreak extends Check implements BlockBreakCheck {
    private double flagBuffer = 0; // If the player flags once, force them to play legit, or we will cancel the tick before.
    private boolean ignorePost = false;
    // A single missed raytrace can be one-tick rotation desync (worse under
    // ViaVersion, which delays/reorders look packets). Nuker / no-rotation break
    // misses on EVERY block, so require consecutive misses before alerting.
    private int consecutiveMisses = 0;
    private static final int MIN_CONSECUTIVE = 3;

    public RotationBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (!player.cameraEntity.isSelf())
            return; // you don't send flying packets when spectating entities
        if (player.inVehicle()) return; // falses
        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) return; // falses

        // Real-time cancel only — alerting is the post-flying check's job (gated
        // on consecutive misses). This just prevents a known-bad break landing.
        if (flagBuffer > 0 && !didRayTraceHit(blockBreak)) {
            ignorePost = true;
            if (flag("pre-flying, action=" + blockBreak.action) && shouldModifyPackets()) {
                blockBreak.cancel();
            }
        }
    }

    @Override
    public void onPostFlyingBlockBreak(BlockBreak blockBreak) {
        if (!player.cameraEntity.isSelf())
            return; // you don't send flying packets when spectating entities
        if (player.inVehicle()) return; // falses
        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) return; // falses

        // Don't flag twice
        if (ignorePost) {
            ignorePost = false;
            return;
        }

        if (didRayTraceHit(blockBreak)) {
            flagBuffer = Math.max(0, flagBuffer - 0.1);
            consecutiveMisses = 0;
        } else {
            flagBuffer = 1;
            consecutiveMisses++;
            if (consecutiveMisses >= MIN_CONSECUTIVE) {
                flagAndAlert("post-flying, action=" + blockBreak.action + " consecutive=" + consecutiveMisses);
            }
        }
    }

    private boolean didRayTraceHit(BlockBreak blockBreak) {
        SimpleCollisionBox box = new SimpleCollisionBox(blockBreak.position);

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();

        // Start checking if player is in the block
        double minEyeHeight = Double.MAX_VALUE;
        double maxEyeHeight = Double.MIN_VALUE;
        for (double height : possibleEyeHeights) {
            minEyeHeight = Math.min(minEyeHeight, height);
            maxEyeHeight = Math.max(maxEyeHeight, height);
        }

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(player.x, player.y + minEyeHeight, player.z, player.x, player.y + maxEyeHeight, player.z);
        eyePositions.expand(player.getMovementThreshold());

        // If the player is inside a block, then they can ray trace through the block and hit the other side of the block
        if (eyePositions.isIntersected(box)) {
            return true;
        }
        // End checking if the player is in the block

        List<Vector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                new Vector3f(player.lastYaw, player.pitch, 0),
                new Vector3f(player.yaw, player.pitch, 0)
        ));

        // 1.9+ players could be a tick behind because we don't get skipped ticks
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            possibleLookDirs.add(new Vector3f(player.lastYaw, player.lastPitch, 0));
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
            possibleLookDirs = Collections.singletonList(new Vector3f(player.yaw, player.pitch, 0));
        }

        final double distance = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        for (double d : possibleEyeHeights) {
            for (Vector3f lookDir : possibleLookDirs) {
                Vector3d starting = new Vector3d(player.x, player.y + d, player.z);
                Ray trace = new Ray(player, starting.getX(), starting.getY(), starting.getZ(), lookDir.getX(), lookDir.getY());
                Pair<Vector3dm, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(distance));

                if (intercept.first() != null) return true;
            }
        }

        return false;
    }
}
