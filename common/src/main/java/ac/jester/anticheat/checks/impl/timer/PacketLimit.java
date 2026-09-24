package ac.jester.anticheat.checks.impl.timer;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

@CheckData(name = "PacketLimit", setback = 10)
public class PacketLimit extends Timer {
    private long limitAbuseOverPing;

    public PacketLimit(GrimPlayer player) {
        super(player);
    }

    @Override
    public void doCheck(final PacketReceiveEvent event) {
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            limitFallBehind();
            return;
        }

        if (timerBalanceRealTime > System.nanoTime()) {
            if (!event.isCancelled()) {
                if (flagAndAlert("ping=" + player.getTransactionPing() + "ms") && shouldSetback()) {
                    player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                }
            }

            timerBalanceRealTime -= 50e6;
        }

        limitFallBehind();
    }

    @Override
    protected void limitFallBehind() {
        long playerClock = lastMovementPlayerClock;
        if (limitAbuseOverPing != -1 && System.nanoTime() - playerClock > limitAbuseOverPing) {
            playerClock = System.nanoTime() - limitAbuseOverPing;
        }
        timerBalanceRealTime = Math.max(timerBalanceRealTime, playerClock - clockDrift);
    }

    @Override
    public void onReload(ConfigManager config) {
        super.onReload(config);
        limitAbuseOverPing = config.getLongElse(getConfigName() + ".high-ping-cap-ms", 1000L);
        if (limitAbuseOverPing != -1) {
            limitAbuseOverPing *= (long) 1e6;
        }
    }
}
