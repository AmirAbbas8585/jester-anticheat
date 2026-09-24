package ac.jester.anticheat.checks.impl.baritone;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.impl.aim.processor.AimProcessor;
import ac.jester.anticheat.checks.type.RotationCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.RotationUpdate;
import ac.jester.anticheat.utils.data.HeadRotation;
import ac.jester.anticheat.utils.math.GrimMath;

@CheckData(name = "Baritone")
public class Baritone extends Check implements RotationCheck {
    private int verbose;

    public Baritone(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        final HeadRotation from = rotationUpdate.getFrom();
        final HeadRotation to = rotationUpdate.getTo();

        final float deltaPitch = Math.abs(to.pitch() - from.pitch());

        if (rotationUpdate.getDeltaXRot() == 0 && deltaPitch > 0 && deltaPitch < 1 && Math.abs(to.pitch()) != 90.0f) {
            if (rotationUpdate.getProcessor().divisorY < GrimMath.MINIMUM_DIVISOR) {
                verbose++;
                if (verbose > 8) {
                    flagAndAlert("Divisor " + AimProcessor.convertToSensitivity(rotationUpdate.getProcessor().divisorX));
                }
            } else {
                verbose = 0;
            }
        }
    }
}
