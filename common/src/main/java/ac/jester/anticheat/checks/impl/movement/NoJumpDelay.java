package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.GameMode;

/**
 * NoJumpDelay — detects jumping too quickly after landing, repeatedly.
 *
 * Vanilla Minecraft enforces a 10-tick (500ms) auto-jump cooldown ONLY while
 * the jump key is held. A player who taps the key with frame-perfect timing
 * CAN legitimately jump 1 tick (50ms) after landing — so a single fast
 * re-jump must never flag.
 *
 * NoJumpDelay hacks remove the held-key cooldown, so the player re-jumps
 * instantly on EVERY landing. The cheat signature is therefore many
 * consecutive sub-150ms land→jump gaps — a human cannot hit frame-perfect
 * taps 6+ times in a row.
 *
 * Detection: When the prediction engine reports a landing, record the
 * timestamp. On the next jump (airborne with upward movement), measure the
 * gap. Count consecutive gaps below maxGapMs; flag at minConsecutive.
 * Any slow re-jump or walking off an edge resets the streak.
 *
 * False-positive mitigations:
 *   - Consecutive requirement (default 6) — tap-jumping cannot sustain this.
 *   - Only flag when ping < 400ms.
 *   - Require isTickingReliablyFor(5).
 *   - Creative / Spectator mode skipped.
 */
@CheckData(name = "NoJumpDelay", description = "Instantly re-jumping on every landing (no jump cooldown)")
public final class NoJumpDelay extends Check implements PostPredictionCheck {

    private long lastLandTime = 0L;
    private int consecutiveFastJumps = 0;

    private int maxGapMs = 150;
    private int minConsecutive = 6;

    public NoJumpDelay(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxGapMs = config.getIntElse("NoJumpDelay.max-gap-ms", 150);
        minConsecutive = config.getIntElse("NoJumpDelay.min-consecutive", 6);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;
        if (!player.isTickingReliablyFor(5)) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        // Just landed: was in air, now on ground
        if (!player.lastOnGround && player.onGround) {
            lastLandTime = System.currentTimeMillis();
            return;
        }

        // Just became airborne: was on ground, now in air
        if (player.lastOnGround && !player.onGround) {
            // Distinguish jump (Y rises) from walking off an edge (Y falls immediately)
            double dy = player.y - player.lastY;
            if (dy < -0.05) {
                // Falling off edge — not a jump, streak broken
                lastLandTime = 0L;
                consecutiveFastJumps = 0;
                return;
            }

            if (lastLandTime == 0L) return;

            long gap = System.currentTimeMillis() - lastLandTime;
            lastLandTime = 0L;

            if (player.getTransactionPing() > 400) {
                consecutiveFastJumps = 0;
                return;
            }

            if (gap < maxGapMs) {
                consecutiveFastJumps++;
                if (consecutiveFastJumps >= minConsecutive) {
                    flagAndAlert(String.format("consecutive=%d gap=%dms max=%dms ping=%dms",
                            consecutiveFastJumps, gap, maxGapMs, player.getTransactionPing()));
                    consecutiveFastJumps = 0;
                }
            } else {
                // Slow re-jump: human timing, streak broken
                consecutiveFastJumps = 0;
            }
        }
    }
}
