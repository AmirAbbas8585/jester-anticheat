package ac.jester.anticheat.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3i;

@CheckData(name = "ScaffoldGoDown", description = "Automated downward staircase block placement")
public final class ScaffoldGoDown extends BlockPlaceCheck {
    private Vector3i lastPlacedPos = null;
    private long lastPlaceTime = 0L;
    private int consecutiveDown = 0;

    private int maxIntervalMs = 250;
    private int minConsecutive = 6;

    public ScaffoldGoDown(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxIntervalMs = config.getIntElse("ScaffoldGoDown.max-interval-ms", 250);
        minConsecutive = config.getIntElse("ScaffoldGoDown.min-consecutive", 6);
        this.cancelVL = config.getIntElse("ScaffoldGoDown.cancelVL", 0);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (!place.isBlock) return;
        if (place.material == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.SCAFFOLDING) return;
        if (player.gamemode == GameMode.CREATIVE) return;
        if (!player.isTickingReliablyFor(3)) return;

        Vector3i pos = place.getPlacedBlockPos();
        long now = System.currentTimeMillis();
        int footY = (int) Math.floor(player.y);

        if (pos.getY() >= footY) {
            resetTracking(pos, now);
            return;
        }

        if (lastPlacedPos != null) {
            long gap = now - lastPlaceTime;

            int dy = pos.getY() - lastPlacedPos.getY();
            int dx = Math.abs(pos.getX() - lastPlacedPos.getX());
            int dz = Math.abs(pos.getZ() - lastPlacedPos.getZ());
            int dHoriz = dx + dz;

            if (dy == -1 && dHoriz <= 2 && gap <= maxIntervalMs) {
                consecutiveDown++;
                if (consecutiveDown >= minConsecutive) {
                    flagAndAlert(String.format("consecutive=%d gap=%dms max=%dms dy=-1 player.y=%.1f",
                            consecutiveDown, gap, maxIntervalMs, player.y));
                    consecutiveDown = 0;
                }
            } else {
                resetTracking(pos, now);
                return;
            }
        } else {
            consecutiveDown = 1;
        }

        lastPlacedPos = pos;
        lastPlaceTime = now;
    }

    private void resetTracking(Vector3i pos, long now) {
        consecutiveDown = 0;
        lastPlacedPos = pos;
        lastPlaceTime = now;
    }
}
