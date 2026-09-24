package ac.jester.anticheat.manager.init.start;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;

public class PacketLimiter implements StartableInitable {
    @Override
    public void start() {
        GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(GrimAPI.INSTANCE.getGrimPlugin(), () -> {
            for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                player.cancelledPackets.set(0);
            }
        }, 1, 20);
    }
}
