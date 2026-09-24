package ac.jester.anticheat.manager.tick.impl;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.player.GrimPlayer;

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
