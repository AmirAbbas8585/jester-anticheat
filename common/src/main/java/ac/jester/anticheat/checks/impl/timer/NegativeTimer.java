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
        // NegativeTimer overrides doCheck() to a no-op (it can only judge once
        // prediction confirms stable ticking), which means it never went through
        // Timer.doCheck()'s flight-toggle/join-grace check added this session.
        // Re-apply the same grace here so a /fly toggle or fresh join/resource-pack
        // load doesn't get misread as "lost time".
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            timerBalanceRealTime = System.nanoTime() + clockDrift;
            return;
        }

        // We can't negative timer check a 1.9+ player who is standing still.
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
        // We don't know if the player is ticking stable, therefore we must wait until prediction
        // determines this.  Do nothing here!
    }

    @Override
    public void onReload(ConfigManager config) {
        clockDrift = (long) (config.getDoubleElse(getConfigName() + ".drift", 1200.0) * 1e6);
    }
}
