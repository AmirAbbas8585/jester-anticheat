package ac.jester.anticheat.manager.tick.impl;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.player.GrimPlayer;

public class ResetTick implements Tickable {
    @Override
    public void tick() {
        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.checkManager.getEntityReplication().tickStartTick();
        }
    }
}
