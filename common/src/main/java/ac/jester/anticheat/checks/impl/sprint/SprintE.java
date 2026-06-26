package ac.jester.anticheat.checks.impl.sprint;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "SprintE", description = "Sprinting while colliding with a wall", setback = 5, experimental = true)
public class SprintE extends Check implements PostPredictionCheck {
    private boolean startedSprintingThisTick, wasHardHorizontalCollision;
    // A player physically wedged in a block corner (real logs: a flat-block
    // corner) has horizontalCollision=true on EVERY tick for as long as they
    // keep holding sprint+forward into it — completely normal, not an exploit
    // (they gain zero speed advantage from being stuck). Without a cooldown
    // this flagged (and force-setback-teleported!) every single tick, racking
    // up 180+ VL in 16 seconds. A real wall-clip speed exploit only needs a
    // handful of ticks to gain its advantage, so it's still caught on the
    // first flag — this only stops re-flagging the SAME ongoing stuck event
    // 20 times a second.
    private long lastFlagTime = 0L;
    private static final long FLAG_COOLDOWN_MS = 750L;

    public SprintE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            if (new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
                startedSprintingThisTick = true;
            }
        }
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (wasHardHorizontalCollision && !startedSprintingThisTick && !player.uncertaintyHandler.isNearGlitchyBlock
                && !player.inVehicle() && !player.uncertaintyHandler.lastVehicleSwitch.hasOccurredSince(0)
                && (!player.wasTouchingWater || player.getClientVersion().isOlderThan(ClientVersion.V_1_13))
                && player.wasLastPredictionCompleteChecked) {
            if (player.isSprinting) {
                long now = System.currentTimeMillis();
                if (now - lastFlagTime >= FLAG_COOLDOWN_MS) {
                    lastFlagTime = now;
                    flagAndAlertWithSetback();
                }
            } else {
                reward();
            }
        }

        wasHardHorizontalCollision = player.horizontalCollision && !player.softHorizontalCollision && player.wasLastPredictionCompleteChecked;
        startedSprintingThisTick = false;
    }
}
