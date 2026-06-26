package ac.jester.anticheat.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.protocol.player.GameMode;

import java.util.ArrayDeque;

/**
 * FastPlace — detects placing blocks at a superhuman rate.
 *
 * A human can realistically place 6-8 blocks per second while tapping.
 * FastPlace cheats bypass the client-side placement cooldown and place
 * far more blocks per second than physically possible.
 *
 * We count block placements in a rolling 1-second window.
 * If the count exceeds the configured max-bps, we flag.
 */
@CheckData(name = "FastPlace", description = "Placing blocks faster than humanly possible")
public final class FastPlace extends BlockPlaceCheck {

    // Vanilla's 4-tick right-click cooldown caps placements at 5/sec no matter
    // how fast you click (godbridge CPS is click rate, not place rate).
    // 10 = 2x that, absorbing network jitter; Meteor FastUse runs at ~20/sec.
    private int maxBps = 10;
    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();

    public FastPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (!place.isBlock) return;
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
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBps = config.getIntElse("FastPlace.max-bps", 10);
        this.cancelVL = config.getIntElse("FastPlace.cancelVL", 0);
    }
}
