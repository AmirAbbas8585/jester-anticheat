package ac.jester.anticheat.manager.tick.impl;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.player.GrimPlayer;

// Check.violations is only ever incremented by flag() — nothing was calling
// Check.reward() (which subtracts each check's own `decay` value, configured
// per-check) except one unrelated VehicleC path. That meant every check's VL
// (Timer, TimerLimit, Simulation, RotationPlace, AutoParkour, ...) silently
// accumulated for the player's whole session and never came back down: a
// handful of harmless blips spread over an hour (a lag spike, a half-block
// edge, an alt-tab freeze) would eventually cross max-violations and kick,
// no matter how well-tuned the per-event thresholds were. Run once a second
// (asyncTick fires every server tick) so each check's `decay` config value
// reads naturally as "VL removed per second".
public class ViolationDecayTick implements Tickable {
    private int counter = 0;

    @Override
    public void tick() {
        if (++counter < 20) return;
        counter = 0;

        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            for (AbstractCheck check : player.checkManager.allChecks.values()) {
                if (check instanceof Check c) {
                    c.reward();
                }
            }
        }
    }
}
