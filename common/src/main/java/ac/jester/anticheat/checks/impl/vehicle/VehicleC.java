package ac.jester.anticheat.checks.impl.vehicle;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;

@CheckData(name = "VehicleC")
public class VehicleC extends Check {
    public VehicleC(GrimPlayer player) {
        super(player);
    }
}
