package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.GameMode;

/**
 * AutoParkour — detects automated parkour via pixel-perfect edge-jump timing.
 *
 * Parkour hacks watch for the exact moment the player is close to the edge of
 * the block they are running across, then fire a jump at the optimal tick to
 * maximise horizontal distance. This results in 95-100% of all jumps being
 * "edge jumps" — jumps taken within the last 15-30% of a block before the
 * open gap.
 *
 * A normal human player might hit an edge jump 30-60% of the time, even when
 * doing legitimate parkour. AutoParkour achieves this virtually every jump,
 * with near-zero variance.
 *
 * Detection: for each jump, compute the fractional offset in the movement
 * direction (0.0 = block center, 1.0 = block edge). An "edge jump" is when
 * this offset > edgeThreshold (default 0.7). We track a rolling window of
 * windowSize jumps. If ≥ minEdgeRatio of them are edge jumps, flag.
 *
 * Minimum movement speed (minSpeedBps) prevents flagging when the player is
 * standing still or barely moving (can't be parkour at low speed).
 */
@CheckData(name = "AutoParkour", description = "Pixel-perfect edge-jump timing consistent with AutoParkour")
public final class AutoParkour extends Check implements PostPredictionCheck {

    private double edgeThreshold = 0.70;
    private int windowSize = 8;
    private double minEdgeRatio = 0.875; // 7/8
    private double minSpeedBps = 0.15; // blocks per tick minimum

    // Rolling window: use a bitmask of last windowSize jumps (1=edge, 0=non-edge)
    private int jumpBits = 0;
    private int jumpCount = 0;
    private int edgeCount = 0;

    public AutoParkour(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        edgeThreshold = config.getDoubleElse("AutoParkour.edge-threshold", 0.70);
        windowSize = config.getIntElse("AutoParkour.window-size", 8);
        minEdgeRatio = config.getDoubleElse("AutoParkour.min-edge-ratio", 0.875);
        minSpeedBps = config.getDoubleElse("AutoParkour.min-speed-bps", 0.15);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;
        if (!player.isTickingReliablyFor(5)) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        // Only check on the tick the player just jumped (was on ground, now airborne)
        if (!player.lastOnGround || player.onGround) return;

        // Must be moving upward (jump, not falling off edge)
        double dy = player.y - player.lastY;
        if (dy < 0.05) return;

        // Must be moving horizontally at minimum speed
        double vx = player.x - player.lastX;
        double vz = player.z - player.lastZ;
        double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizontalSpeed < minSpeedBps) {
            // Player isn't moving forward — reset window (can't do parkour standing still)
            jumpBits = 0;
            jumpCount = 0;
            edgeCount = 0;
            return;
        }

        // Calculate fractional block offset in direction of movement
        double fracX = ((player.x % 1.0) + 1.0) % 1.0;
        double fracZ = ((player.z % 1.0) + 1.0) % 1.0;

        double edgeOffset;
        if (Math.abs(vx) >= Math.abs(vz)) {
            // Primarily moving in X direction
            edgeOffset = (vx > 0) ? fracX : (1.0 - fracX);
        } else {
            // Primarily moving in Z direction
            edgeOffset = (vz > 0) ? fracZ : (1.0 - fracZ);
        }

        boolean isEdgeJump = edgeOffset >= edgeThreshold;

        // Update rolling window: jumpBits holds the last windowSize jumps
        // (bit 0 = newest, bit windowSize-1 = oldest). Shift out the oldest
        // bit's contribution before adding the new one — keeps edgeCount exact.
        if (jumpCount >= windowSize) {
            if ((jumpBits & (1 << (windowSize - 1))) != 0) edgeCount--;
        } else {
            jumpCount++;
        }
        jumpBits = (jumpBits << 1) & ((1 << windowSize) - 1);
        if (isEdgeJump) {
            jumpBits |= 1;
            edgeCount++;
        }

        // Check ratio when we have enough samples
        if (jumpCount >= windowSize) {
            double ratio = (double) edgeCount / windowSize;
            if (ratio >= minEdgeRatio && player.getTransactionPing() < 400) {
                flagAndAlert(String.format("edge_ratio=%.0f%% threshold=%.0f%% offset=%.2f speed=%.2f ping=%dms",
                        ratio * 100, minEdgeRatio * 100, edgeOffset, horizontalSpeed,
                        player.getTransactionPing()));
                // Reset after flagging to avoid immediate re-flag
                jumpBits = 0;
                jumpCount = 0;
                edgeCount = 0;
            }
        }
    }
}
