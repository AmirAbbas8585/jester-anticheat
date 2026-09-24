package ac.jester.anticheat.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.protocol.player.GameMode;

import java.util.ArrayDeque;

@CheckData(name = "FastPlace", description = "Placing blocks faster than humanly possible")
public final class FastPlace extends BlockPlaceCheck {
    private int maxBps = 10;
    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();

    public FastPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (!place.isBlock) return;
        if (place.material == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.SCAFFOLDING) return;
        if (player.gamemode == GameMode.CREATIVE) return;
        if (!player.isTickingReliablyFor(3)) return;

        long now = System.currentTimeMillis();
        timestamps.addLast(now);

        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > 1000L) {
            timestamps.pollFirst();
        }

        int bps = timestamps.size();
        if (bps > maxBps) {
            if (flagAndAlert(String.format("bps=%d max=%d", bps, maxBps)) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
            timestamps.clear();
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBps = config.getIntElse("FastPlace.max-bps", 10);
        this.cancelVL = config.getIntElse("FastPlace.cancelVL", 0);
    }
}
