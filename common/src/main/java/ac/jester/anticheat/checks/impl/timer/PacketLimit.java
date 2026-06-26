package ac.jester.anticheat.checks.impl.timer;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

// This works around 1.3 timer, to prevent too high abuse - maybe there's a better solution?
@CheckData(name = "PacketLimit", setback = 10)
public class PacketLimit extends Timer {

    // At what ping should we start to limit the balance advantage? (nanos)
    private long limitAbuseOverPing;

    public PacketLimit(GrimPlayer player) {
        super(player);
    }

    @Override
    public void doCheck(final PacketReceiveEvent event) {
        // Grace the flight (/fly) toggle window + post-join / resource-pack load —
        // same as Timer.doCheck, which this overrides (so it isn't inherited)
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            limitFallBehind();
            return;
        }

        // 1:1 with Timer minus cancelling the packet
        if (timerBalanceRealTime > System.nanoTime()) {
            // If timer check already flagged, don't flag.
            if (!event.isCancelled()) {
                // Same reasoning as Timer.doCheck: surface ping so a kick caused
                // by a real connection spike doesn't look identical to a hack.
                if (flagAndAlert("ping=" + player.getTransactionPing() + "ms") && shouldSetback()) {
                    player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                }
            }

            // Reset the violation by 1 movement
            timerBalanceRealTime -= 50e6;
        }

        limitFallBehind();
    }

    @Override
    protected void limitFallBehind() {
        // Limit using transaction ping if over 1000ms (default)
        long playerClock = lastMovementPlayerClock;
        if (limitAbuseOverPing != -1 && System.nanoTime() - playerClock > limitAbuseOverPing) {
            playerClock = System.nanoTime() - limitAbuseOverPing;
        }
        timerBalanceRealTime = Math.max(timerBalanceRealTime, playerClock - clockDrift);
    }

    @Override
    public void onReload(ConfigManager config) {
        super.onReload(config);
        limitAbuseOverPing = config.getLongElse(getConfigName() + ".ping-abuse-limit-threshold", 1000L);
        if (limitAbuseOverPing != -1) {
            limitAbuseOverPing *= (long) 1e6;
        }
    }
}
