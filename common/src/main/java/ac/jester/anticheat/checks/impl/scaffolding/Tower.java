package ac.jester.anticheat.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * Tower — detects placing blocks directly under yourself while moving upward too rapidly.
 *
 * Legitimate tower building requires a full jump cycle (~4-5 ticks) between placements.
 * The Tower/Scaffold Tower hack bypasses this by placing blocks every tick (50ms) while
 * continuously moving upward, which is physically impossible in vanilla.
 *
 * Detection: track the interval between consecutive "under-self" block placements
 * while the player has upward movement. Intervals below the threshold are flagged.
 */
@CheckData(name = "Tower", description = "Placing blocks under self while jumping faster than vanilla allows")
public final class Tower extends BlockPlaceCheck {

    private long lastTowerPlace = 0L;
    private int minIntervalMs = 100;
    private int minConsecutive = 2;
    private int consecutiveFast = 0;

    public Tower(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (!place.isBlock) return;
        if (player.gamemode == GameMode.CREATIVE) return;
        if (!player.isTickingReliablyFor(3)) return;

        // Only care about placements on top of a block (UP face = block going above click target)
        if (place.getFace() != BlockFace.UP) return;

        // Player must be moving upward
        if (player.y <= player.lastY) return;

        // Check if the placed block is directly below the player's feet (same XZ column)
        Vector3i placed = place.getPlacedBlockPos();
        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y);
        int pz = (int) Math.floor(player.z);

        // Allow ±1 in X/Z to account for player width (0.6 blocks wide)
        boolean sameColumn = Math.abs(placed.getX() - px) <= 1
                && Math.abs(placed.getZ() - pz) <= 1
                && (placed.getY() == py || placed.getY() == py - 1);

        if (!sameColumn) return;

        long now = System.currentTimeMillis();
        long gap = now - lastTowerPlace;

        if (lastTowerPlace != 0L && gap < minIntervalMs) {
            // Single fast gap can be network jitter bunching two legit
            // placements together — require consecutive occurrences
            consecutiveFast++;
            if (consecutiveFast >= minConsecutive) {
                if (flagAndAlert(String.format("gap=%dms min=%dms consecutive=%d dy=+%.3f",
                        gap, minIntervalMs, consecutiveFast, player.y - player.lastY))
                        && shouldModifyPackets() && shouldCancel()) {
                    place.resync();
                }
                consecutiveFast = 0;
            }
        } else {
            consecutiveFast = 0;
        }

        lastTowerPlace = now;
    }

    @Override
    public void onReload(ConfigManager config) {
        minIntervalMs = config.getIntElse("Tower.min-interval-ms", 100);
        minConsecutive = config.getIntElse("Tower.min-consecutive", 2);
        this.cancelVL = config.getIntElse("Tower.cancelVL", 0);
    }
}
