package ac.jester.anticheat.checks.type;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.utils.anticheat.update.RotationUpdate;

public interface RotationCheck extends AbstractCheck {

    default void process(final RotationUpdate rotationUpdate) {
    }
}
