package ac.jester.anticheat.checks.impl.movement;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PositionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PositionUpdate;

public class PredictionRunner extends Check implements PositionCheck {
    public PredictionRunner(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPositionUpdate(final PositionUpdate positionUpdate) {
        if (!player.inVehicle()) {
            player.movementCheckRunner.processAndCheckMovementPacket(positionUpdate);
        }
    }
}
