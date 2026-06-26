package ac.jester.anticheat.checks.impl.scaffolding;

import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.anticheat.update.RotationUpdate;

@CheckData(name = "DuplicateRotPlace", experimental = true)
public class DuplicateRotPlace extends BlockPlaceCheck {

    private float deltaX, deltaY;
    private float lastPlacedDeltaX;
    private double lastPlacedDeltaDotsX;
    private double deltaDotsX;
    private boolean rotated = false;
    // A straight-line build (wall/floor) naturally needs nearly the same look
    // angle for several consecutive placements — that alone isn't a macro.
    // Require the identical-delta pattern to repeat before flagging.
    private int consecutiveMatches = 0;
    private static final int MIN_CONSECUTIVE = 2;

    public DuplicateRotPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        deltaX = rotationUpdate.getDeltaXRotABS();
        deltaY = rotationUpdate.getDeltaYRotABS();
        deltaDotsX = rotationUpdate.getProcessor().deltaDotsX;
        rotated = true;
    }

    @Override
    public void onPostFlyingBlockPlace(BlockPlace place) {
        if (rotated && !player.inVehicle()) {
            if (deltaX > 2) {
                float xDiff = Math.abs(deltaX - lastPlacedDeltaX);
                double xDiffDots = Math.abs(deltaDotsX - lastPlacedDeltaDotsX);

                if (xDiff < 0.0001) {
                    consecutiveMatches++;
                    if (consecutiveMatches >= MIN_CONSECUTIVE) {
                        flagAndAlert("x=" + xDiff + " xdots=" + xDiffDots + " y=" + deltaY + " consecutive=" + consecutiveMatches);
                    }
                } else {
                    reward();
                    consecutiveMatches = 0;
                }
            } else {
                reward();
            }
            this.lastPlacedDeltaX = deltaX;
            this.lastPlacedDeltaDotsX = deltaDotsX;
            rotated = false;
        }
    }
}
