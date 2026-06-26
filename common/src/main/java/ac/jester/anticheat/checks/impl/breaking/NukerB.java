package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * Nuker B — Rotation-based multi-block break detection.
 *
 * Nuker hacks break blocks in a sphere/cube around the player without changing
 * their look direction. In vanilla, breaking a block requires the player's crosshair
 * to be physically pointing at the block face. The server tracks this via
 * RotationBreak — but NukerB adds a complementary pattern-based signal:
 *
 * If the player breaks blocks at positions that differ significantly in
 * direction from their current look, within a short time window, that pattern
 * is impossible without a hacked client.
 *
 * Detection: Within a rolling window, if multiple FINISHED_DIGGING events occur
 * at block positions that span more than maxSpanDegrees of directional spread
 * (measured from the player's current yaw), it indicates automated multi-block breaking.
 *
 * Secondary detection: In Creative mode, flag if break rate exceeds
 * maxCreativeBps (creative players still have physical limitations on how fast
 * they can manually click blocks in different positions).
 */
@CheckData(name = "Nuker", configName = "NukerB",
        description = "Breaking blocks in multiple directions rapidly (Nuker pattern)")
public final class NukerB extends Check implements BlockBreakCheck {

    private static final long SPAN_WINDOW_MS = 500L;
    private double maxSpanDegrees = 120.0;
    private int maxCreativeBps = 8;

    // Track the yaw direction of recent breaks
    private float firstBreakYaw = Float.NaN;
    private long windowStart = 0L;
    private int breakCount = 0;
    private double maxYawSeenInWindow = 0;

    // Creative mode rate tracking
    private long creativeBurstStart = 0L;
    private int creativeBurstCount = 0;

    public NukerB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxSpanDegrees = config.getDoubleElse("NukerB.max-span-degrees", 120.0);
        maxCreativeBps = config.getIntElse("NukerB.max-creative-bps", 8);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.FINISHED_DIGGING) return;
        if (!player.isTickingReliablyFor(3)) return;

        long now = System.currentTimeMillis();
        Vector3i pos = blockBreak.position;

        // Creative mode rate check
        if (player.gamemode == GameMode.CREATIVE) {
            if (now - creativeBurstStart > 1000L) {
                creativeBurstStart = now;
                creativeBurstCount = 0;
            }
            creativeBurstCount++;
            if (creativeBurstCount > maxCreativeBps) {
                flagAndAlert(String.format("creative_bps=%d max=%d", creativeBurstCount, maxCreativeBps));
                creativeBurstCount = 0;
            }
            return;
        }

        // Direction-span check (survival only)
        // Calculate horizontal direction from player to broken block
        double dx = pos.getX() + 0.5 - player.x;
        double dz = pos.getZ() + 0.5 - player.z;
        float breakYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        if (now - windowStart > SPAN_WINDOW_MS || Float.isNaN(firstBreakYaw)) {
            // Start new window
            windowStart = now;
            firstBreakYaw = breakYaw;
            maxYawSeenInWindow = 0;
            breakCount = 1;
            return;
        }

        breakCount++;

        // Compute yaw diff from first break
        double yawDiff = breakYaw - firstBreakYaw;
        yawDiff = ((yawDiff % 360) + 540) % 360 - 180;
        double absYawDiff = Math.abs(yawDiff);
        maxYawSeenInWindow = Math.max(maxYawSeenInWindow, absYawDiff);

        // Flag if multiple blocks broken spanning large yaw spread within short window
        if (breakCount >= 3 && maxYawSeenInWindow > maxSpanDegrees) {
            flagAndAlert(String.format("span=%.1f° max=%.0f° breaks=%d in %dms",
                    maxYawSeenInWindow, maxSpanDegrees, breakCount,
                    now - windowStart));
            // Reset window
            firstBreakYaw = breakYaw;
            windowStart = now;
            breakCount = 1;
            maxYawSeenInWindow = 0;
        }
    }
}
