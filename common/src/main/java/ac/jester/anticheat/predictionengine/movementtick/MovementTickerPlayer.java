package ac.jester.anticheat.predictionengine.movementtick;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineLava;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineNormal;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineWater;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineWaterLegacy;
import ac.jester.anticheat.utils.nmsutil.BlockProperties;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class MovementTickerPlayer extends MovementTicker {
    public MovementTickerPlayer(GrimPlayer player) {
        super(player);
    }

    @Override
    public void doWaterMove(float swimSpeed, boolean isFalling, float swimFriction) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
            new PredictionEngineWater().guessBestMovement(swimSpeed, player, isFalling, player.gravity, swimFriction);
        } else {
            new PredictionEngineWaterLegacy().guessBestMovement(swimSpeed, player, swimFriction);
        }
    }

    @Override
    public void doLavaMove() {
        new PredictionEngineLava().guessBestMovement(0.02F, player);
    }

    @Override
    public void doNormalMove(float blockFriction) {
        new PredictionEngineNormal().guessBestMovement(BlockProperties.getFrictionInfluencedSpeed(blockFriction, player), player);
    }
}
