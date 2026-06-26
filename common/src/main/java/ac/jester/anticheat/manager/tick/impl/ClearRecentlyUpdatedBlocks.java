package ac.jester.anticheat.manager.tick.impl;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.player.GrimPlayer;

public class ClearRecentlyUpdatedBlocks implements Tickable {

    private static final int maxTickAge = 2;

    @Override
    public void tick() {
        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.blockHistory.cleanup(GrimAPI.INSTANCE.getTickManager().currentTick - maxTickAge);
        }
    }
}
