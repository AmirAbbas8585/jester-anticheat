package ac.jester.anticheat.utils.nmsutil;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.MainSupportingBlockData;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

@UtilityClass
public class MainSupportingBlockPosFinder {
    public MainSupportingBlockData findMainSupportingBlockPos(GrimPlayer player, MainSupportingBlockData lastSupportingBlock, Vector3d lastMovement, SimpleCollisionBox maxPose, boolean isOnGround) {
        if (!isOnGround) {
            return new MainSupportingBlockData(null, false);
        }

        SimpleCollisionBox slightlyBelowPlayer = new SimpleCollisionBox(maxPose.minX, maxPose.minY - 1.0E-6D, maxPose.minZ, maxPose.maxX, maxPose.minY, maxPose.maxZ);

        Vector3i supportingBlock = findSupportingBlock(player, slightlyBelowPlayer);
        if (supportingBlock == null && (!lastSupportingBlock.lastOnGroundAndNoBlock())) {
            if (lastMovement != null) {
                SimpleCollisionBox aabb2 = slightlyBelowPlayer.offset(-lastMovement.x, 0.0D, -lastMovement.z);
                return new MainSupportingBlockData(findSupportingBlock(player, aabb2), true);
            }
        } else {
            return new MainSupportingBlockData(supportingBlock, true);
        }

        return new MainSupportingBlockData(null, true);
    }

    private @Nullable Vector3i findSupportingBlock(@NotNull GrimPlayer player, @NotNull SimpleCollisionBox searchBox) {
        Vector3d playerPos = new Vector3d(player.x, player.y, player.z);

        AtomicReference<Vector3i> bestBlockPos = new AtomicReference<>();
        AtomicDouble blockPosDistance = new AtomicDouble(Double.MAX_VALUE);

        Collisions.forEachCollisionBox(player, searchBox, (data, blockPos) -> {
            Vector3d center = new Vector3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            double distance = playerPos.distanceSquared(center);

            if (distance < blockPosDistance.get() || distance == blockPosDistance.get() && (bestBlockPos.get() == null || firstHasPriorityOverSecond(blockPos, bestBlockPos.get()))) {
                bestBlockPos.set(blockPos);
                blockPosDistance.set(distance);
            }
        });

        return bestBlockPos.get();
    }

    private boolean firstHasPriorityOverSecond(@NotNull Vector3i first, @NotNull Vector3i second) {
        if (first.getY() < second.getY()) return true;

        double sumX = second.getX() - first.getX();
        double sumY = second.getZ() - first.getZ();

        double horizontalSumTotal = sumX + sumY;
        if (horizontalSumTotal == 0) {
            return sumX < 0;
        }

        return horizontalSumTotal < 0;
    }
}
