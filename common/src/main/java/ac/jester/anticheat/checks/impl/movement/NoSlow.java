package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

@CheckData(name = "NoSlow", description = "Was not slowed while using an item", setback = 5)
public class NoSlow extends Check implements PostPredictionCheck {
    public boolean didSlotChangeLastTick = false;
    public boolean flaggedLastTick = false;
    private double offsetToFlag;
    private double bestOffset = 1;

    public NoSlow(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (player.packetStateData.isSlowedByUsingItem()) {
            if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8) && didSlotChangeLastTick) {
                didSlotChangeLastTick = false;
                flaggedLastTick = false;
            }

            if (bestOffset > offsetToFlag) {
                if (flaggedLastTick) {
                    flagAndAlertWithSetback();
                }
                flaggedLastTick = true;
            } else {
                reward();
                flaggedLastTick = false;
            }
        }
        bestOffset = 1;
    }

    public void handlePredictionAnalysis(double offset) {
        bestOffset = Math.min(bestOffset, offset);
    }

    @Override
    public void onReload(ConfigManager config) {
        offsetToFlag = config.getDoubleElse(getConfigName() + ".min-offset", 0.001);
    }
}
