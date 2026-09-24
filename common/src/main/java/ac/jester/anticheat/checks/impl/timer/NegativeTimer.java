package ac.jester.anticheat.checks.impl.timer;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

@CheckData(name = "NegativeTimer", setback = -1, experimental = true)
public class NegativeTimer extends Timer implements PostPredictionCheck {
    public NegativeTimer(GrimPlayer player) {
        super(player);
        timerBalanceRealTime = System.nanoTime() + clockDrift;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            timerBalanceRealTime = System.nanoTime() + clockDrift;
            return;
        }

        if (player.uncertaintyHandler.lastPointThree.hasOccurredSince(2) || !predictionComplete.isChecked()) {
            timerBalanceRealTime = System.nanoTime() + clockDrift;
        }

        if (timerBalanceRealTime < lastMovementPlayerClock - clockDrift) {
            int lostMS = (int) ((System.nanoTime() - timerBalanceRealTime) / 1e6);
            flagAndAlertWithSetback("-" + lostMS);
            timerBalanceRealTime += 50e6;
        }
    }

    @Override
    public void doCheck(final PacketReceiveEvent event) {
    }

    @Override
    public void onReload(ConfigManager config) {
        clockDrift = (long) (config.getDoubleElse(getConfigName() + ".max-behind-ms", 1200.0) * 1e6);
    }
}
