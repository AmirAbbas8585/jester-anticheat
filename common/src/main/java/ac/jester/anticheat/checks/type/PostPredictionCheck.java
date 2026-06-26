package ac.jester.anticheat.checks.type;

import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;

public interface PostPredictionCheck extends PacketCheck {

    default void onPredictionComplete(final PredictionComplete predictionComplete) {
    }
}
