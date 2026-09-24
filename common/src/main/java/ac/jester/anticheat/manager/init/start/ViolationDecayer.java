package ac.jester.anticheat.manager.init.start;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.player.GrimPlayer;

public class ViolationDecayer implements StartableInitable {
    @Override
    public void start() {
        GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(
                GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                    for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                        for (AbstractCheck check : player.checkManager.allChecks.values()) {
                            if (check instanceof Check c) c.decayViolations();
                        }
                    }
                }, 20, 20);
    }
}
