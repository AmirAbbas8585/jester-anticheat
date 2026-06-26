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
        // Exemptions
        // Don't check players in spectator
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_8) && player.gamemode == GameMode.SPECTATOR)
            return;
        // And don't check this long list of ground exemptions
        if (player.exemptOnGround() || !predictionComplete.isChecked()) return;
        // Don't check if the player was on a ghost block
        if (player.getSetbackTeleportUtil().blockOffsets) return;
        // Viaversion sends wrong ground status... (doesn't matter but is annoying)
        if (player.packetStateData.lastPacketWasTeleport) return;

        // GSit: sitting/laying players report wrong ground status — skip
        if (ExemptionProvider.safe().isSitting(player)) return;

        // Post-join (mid-air spawn / chunk load) and resource-pack download —
        // ground status desyncs in these transient states
        if (player.inJoinOrLoadGrace()) return;

        // Beds have an asymmetric, reduced-height collision shape — real logs
        // showed jump-spamming on/near a bed corner repeatedly claiming a
        // ground status mismatch. Same root cause as the MovementA
        // bed-threshold exemption; not a movement exploit.
        if (Materials.isBed(player.compensatedWorld.getBlockType(player.x, player.y - 0.1, player.z))) return;

        if (player.clientClaimsLastOnGround != player.onGround) {
            flagAndAlertWithSetback("claimed " + player.clientClaimsLastOnGround);
            player.checkManager.getNoFall().flipPlayerGroundStatus = true;
        }
    }
}
