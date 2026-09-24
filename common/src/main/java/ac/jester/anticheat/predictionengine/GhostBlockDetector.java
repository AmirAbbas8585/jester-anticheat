package ac.jester.anticheat.predictionengine;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class GhostBlockDetector extends Check implements PostPredictionCheck {
    public GhostBlockDetector(GrimPlayer player) {
        super(player);
    }

    public static boolean isGhostBlock(GrimPlayer player) {
        if (player.uncertaintyHandler.isOrWasNearGlitchyBlock) {
            return true;
        }

        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
            SimpleCollisionBox largeExpandedBB = player.boundingBox.copy().expand(12, 0.5, 12);

            for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
                if (entity.isBoat) {
                    if (entity.getPossibleCollisionBoxes().isIntersected(largeExpandedBB)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (predictionComplete.getOffset() < 0.001 && (player.clientClaimsLastOnGround == player.onGround || player.inVehicle()))
            return;

        boolean shouldResync = isGhostBlock(player);

        if (shouldResync) {
            if (player.clientClaimsLastOnGround != player.onGround) {
                player.onGround = player.clientClaimsLastOnGround;
            }

            predictionComplete.setOffset(0);
            player.getSetbackTeleportUtil().executeForceResync();
        }
    }
}
