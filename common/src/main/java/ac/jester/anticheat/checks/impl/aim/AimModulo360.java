package ac.jester.anticheat.checks.impl.aim;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.RotationCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.RotationUpdate;

@CheckData(name = "AimModulo360", decay = 0.005)
public class AimModulo360 extends Check implements RotationCheck {
    private float lastDeltaYaw;

    public AimModulo360(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            lastDeltaYaw = rotationUpdate.getDeltaXRot();
            return;
        }

        if (player.yaw < 360 && player.yaw > -360 && Math.abs(rotationUpdate.getDeltaXRot()) > 320 && Math.abs(lastDeltaYaw) < 30) {
            flagAndAlert();
        } else {
            reward();
        }

        lastDeltaYaw = rotationUpdate.getDeltaXRot();
    }
}
