package ac.jester.anticheat.checks.type;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.utils.anticheat.update.PositionUpdate;

public interface PositionCheck extends AbstractCheck {

    default void onPositionUpdate(final PositionUpdate positionUpdate) {
    }
}
