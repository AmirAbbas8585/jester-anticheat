package ac.jester.anticheat.checks.impl.scaffolding;

import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@CheckData(name = "PositionPlace", description = "Placed a block against a hidden face")
public class PositionPlace extends BlockPlaceCheck {
    public PositionPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (place.material == StateTypes.SCAFFOLDING || player.inVehicle()) return;

        SimpleCollisionBox combined = getCombinedBox(place);

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();
        double minEyeHeight = Double.MAX_VALUE;
        double maxEyeHeight = Double.MIN_VALUE;
        for (double height : possibleEyeHeights) {
            minEyeHeight = Math.min(minEyeHeight, height);
            maxEyeHeight = Math.max(maxEyeHeight, height);
        }
        double movementThreshold = !player.packetStateData.didLastMovementIncludePosition || player.canSkipTicks() ? player.getMovementThreshold() : 0;

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(player.x, player.y + minEyeHeight, player.z, player.x, player.y + maxEyeHeight, player.z);
        eyePositions.expand(movementThreshold);

        if (eyePositions.isIntersected(combined)) {
            return;
        }

        boolean flag = switch (place.getFace()) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
            default -> false;
        };

        if (flag && flagAndAlert() && shouldModifyPackets() && shouldCancel()) {
            place.resync();
        }
    }
}
