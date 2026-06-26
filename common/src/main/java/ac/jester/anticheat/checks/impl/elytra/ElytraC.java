package ac.jester.anticheat.checks.impl.elytra;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "ElytraC", description = "Started gliding too frequently")
public class ElytraC extends Check implements PostPredictionCheck {
    private boolean glideThisTick, glideLastTick, setback;
    private int flags;
    public boolean exempt;

    public ElytraC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) {
            return;
        }

        if (!player.cameraEntity.isSelf()) {
            glideThisTick = glideLastTick = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION && new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA && !exempt) {
            if (glideThisTick || glideLastTick) {
                if (player.canSkipTicks()) {
                    flags++;
                } else {
                    if (flagAndAlert()) {
                        setback = true;
                        if (shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                            player.resyncPose();
                        }
                    }
                }
            }

            glideThisTick = true;
        }

        if (isTickPacket(event.getPacketType())) {
            glideLastTick = glideThisTick;
            glideThisTick = exempt = false;
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (player.canSkipTicks()) {
            if (player.isTickingReliablyFor(3)) {
                for (; flags > 0; flags--) {
                    flagAndAlert();
                }
            }

            flags = 0;
            setback = false;
        }

        if (setback) {
            setback = false;
            setbackIfAboveSetbackVL();
        }
    }
}
