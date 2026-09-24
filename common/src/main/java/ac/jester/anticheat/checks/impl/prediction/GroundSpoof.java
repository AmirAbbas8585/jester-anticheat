package ac.jester.anticheat.checks.impl.prediction;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.hooks.ExemptionProvider;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "GroundSpoof", setback = 10, decay = 0.01)
public class GroundSpoof extends Check implements PostPredictionCheck {
    public GroundSpoof(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_8) && player.gamemode == GameMode.SPECTATOR)
            return;
        if (player.exemptOnGround() || !predictionComplete.isChecked()) return;
        if (player.getSetbackTeleportUtil().blockOffsets) return;
        if (player.packetStateData.lastPacketWasTeleport) return;

        if (ExemptionProvider.safe().isSitting(player)) return;

        if (player.inJoinOrLoadGrace()) return;

        if (Materials.isBed(player.compensatedWorld.getBlockType(player.x, player.y - 0.1, player.z))) return;

        if (player.clientClaimsLastOnGround != player.onGround) {
            flagAndAlertWithSetback("claimed " + player.clientClaimsLastOnGround);
            player.checkManager.getNoFall().flipPlayerGroundStatus = true;
        }
    }
}
