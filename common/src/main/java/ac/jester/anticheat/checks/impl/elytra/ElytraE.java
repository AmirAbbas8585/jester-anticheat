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

@CheckData(name = "ElytraE", description = "Started gliding while flying", experimental = true)
public class ElytraE extends Check implements PostPredictionCheck {
    private boolean setback;

    public ElytraE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) {
            return;
        }

        // player.isFlying is Grim's own simulated state, which can briefly lag
        // behind the client right after joining/loading or toggling /fly.
        if (player.inJoinOrLoadGrace() || System.currentTimeMillis() - player.lastFlightToggleTime < 1000L) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION
                && new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA
                && player.isFlying
                && flagAndAlert()
        ) {
            setback = true;
            if (shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
                player.resyncPose();
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (setback) {
            setbackIfAboveSetbackVL();
            setback = false;
        }
    }
}
