package ac.jester.anticheat.checks.type;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.utils.anticheat.update.VehiclePositionUpdate;

public interface VehicleCheck extends AbstractCheck {

    void process(final VehiclePositionUpdate vehicleUpdate);
}
