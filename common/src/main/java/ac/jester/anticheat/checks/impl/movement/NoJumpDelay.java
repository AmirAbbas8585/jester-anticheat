package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.GameMode;

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

        if (!player.lastOnGround && player.onGround) {
            lastLandTime = System.currentTimeMillis();
            return;
        }

        if (player.lastOnGround && !player.onGround) {
            double dy = player.y - player.lastY;
            if (dy < -0.05) {
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
                consecutiveFastJumps = 0;
            }
        }
    }
}
