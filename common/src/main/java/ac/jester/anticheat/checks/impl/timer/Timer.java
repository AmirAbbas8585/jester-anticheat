package ac.jester.anticheat.checks.impl.timer;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

@CheckData(name = "Timer", configName = "TimerA", setback = 10)
public class Timer extends Check implements PacketCheck {
    protected long timerBalanceRealTime = 0;

    protected long knownPlayerClockTime = (long) (System.nanoTime() - 6e10);
    protected long lastMovementPlayerClock = (long) (System.nanoTime() - 6e10);

    protected long clockDrift;

    protected boolean hasGottenMovementAfterTransaction = false;

    public Timer(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (hasGottenMovementAfterTransaction && checkForTransaction(event.getPacketType())) {
            knownPlayerClockTime = lastMovementPlayerClock;
            lastMovementPlayerClock = player.getPlayerClockAtLeast();
            hasGottenMovementAfterTransaction = false;
        }

        if (!shouldCountPacketForTimer(event.getPacketType())) return;

        hasGottenMovementAfterTransaction = true;
        timerBalanceRealTime += 50e6;

        doCheck(event);
    }

    public void doCheck(final PacketReceiveEvent event) {
        if (System.currentTimeMillis() - player.lastFlightToggleTime < 1000L
                || player.inJoinOrLoadGrace()) {
            limitFallBehind();
            return;
        }

        if (timerBalanceRealTime > System.nanoTime()) {
            if (flagAndAlert("ping=" + player.getTransactionPing() + "ms")) {
                if (shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }

                if (shouldSetback()) {
                    player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                }
            }

            timerBalanceRealTime -= 50e6;
        }

        limitFallBehind();
    }

    protected void limitFallBehind() {
        timerBalanceRealTime = Math.max(timerBalanceRealTime, lastMovementPlayerClock - clockDrift);
    }

    public boolean checkForTransaction(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.PONG ||
                packetType == PacketType.Play.Client.WINDOW_CONFIRMATION;
    }

    public boolean shouldCountPacketForTimer(PacketTypeCommon packetType) {
        return isTickPacket(packetType);
    }

    @Override
    public void onReload(ConfigManager config) {
        clockDrift = (long) (config.getDoubleElse(getConfigName() + ".max-behind-ms", 120.0) * 1e6);
    }
}
