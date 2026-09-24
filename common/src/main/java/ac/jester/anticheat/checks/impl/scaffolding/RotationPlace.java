package ac.jester.anticheat.checks.impl.scaffolding;

import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Ray;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationPlace", description = "Placed a block while not looking at it")
public class RotationPlace extends BlockPlaceCheck {
    private double flagBuffer = 0;
    private boolean ignorePost = false;

    public RotationPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (place.material == StateTypes.SCAFFOLDING) return;
        if (!player.cameraEntity.isSelf())
            return;
        if (player.inVehicle()) return;
        if (flagBuffer >= 2 && !didRayTraceHit(place)) {
            ignorePost = true;
            if (flagAndAlert("pre-flying") && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }

    @Override
    public void onPostFlyingBlockPlace(BlockPlace place) {
        if (place.material == StateTypes.SCAFFOLDING) return;
        if (!player.cameraEntity.isSelf())
            return;
        if (player.inVehicle()) return;

        if (ignorePost) {
            ignorePost = false;
            return;
        }

        boolean hit = didRayTraceHit(place);
        if (!hit) {
            flagBuffer = Math.min(4, flagBuffer + 1);
            if (flagBuffer >= 2) {
                flagAndAlert("post-flying");
            }
        } else {
            flagBuffer = Math.max(0, flagBuffer - 0.5);
        }
    }

    private boolean didRayTraceHit(BlockPlace place) {
        SimpleCollisionBox box = new SimpleCollisionBox(place.position);

        List<Vector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                new Vector3f(player.yaw, player.pitch, 0),
                new Vector3f(player.lastYaw, player.pitch, 0)
        ));

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();

        double minEyeHeight = Double.MAX_VALUE;
        double maxEyeHeight = Double.MIN_VALUE;
        for (double height : possibleEyeHeights) {
            minEyeHeight = Math.min(minEyeHeight, height);
            maxEyeHeight = Math.max(maxEyeHeight, height);
        }

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(player.x, player.y + minEyeHeight, player.z, player.x, player.y + maxEyeHeight, player.z);
        eyePositions.expand(player.getMovementThreshold());

        if (eyePositions.isIntersected(box)) {
            return true;
        }

        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            possibleLookDirs.add(new Vector3f(player.lastYaw, player.lastPitch, 0));
        }

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
