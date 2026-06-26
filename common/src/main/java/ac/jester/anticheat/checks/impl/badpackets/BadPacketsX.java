package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "BadPacketsX", experimental = true)
public class BadPacketsX extends Check implements PostPredictionCheck {
    private boolean sprint;
    private boolean sneak;
    private int flags;

    public BadPacketsX(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) {
            if (flags > 0) {
                setbackIfAboveSetbackVL();
            }

            flags = 0;
            return;
        }

        if (player.isTickingReliablyFor(3)) {
            for (; flags > 0; flags--) {
                flagAndAlertWithSetback();
            }
        }

        flags = 0;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.cameraEntity.isSelf() || isTickPacket(event.getPacketType())) {
            sprint = sneak = false;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            switch (new WrapperPlayClientEntityAction(event).getAction()) {
                case START_SNEAKING, STOP_SNEAKING -> {
                    if (sneak) {
                        if (player.canSkipTicks() || flagAndAlert()) {
                            flags++;
                        }
                    }

                    sneak = true;
                }
                case START_SPRINTING, STOP_SPRINTING -> {
                    if (player.inVehicle()) {
                        return;
                    }

                    if (sprint) {
                        if (player.canSkipTicks() || flagAndAlert()) {
                            flags++;
                        }
                    }

                    sprint = true;
                }
            }
        }
    }
}
