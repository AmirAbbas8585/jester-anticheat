package ac.jester.anticheat.utils.latency;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;

import java.util.HashSet;
import java.util.Set;

public class CompensatedFireworks extends Check implements PostPredictionCheck {
    private final Set<Integer> activeFireworks = new HashSet<>();
    private final Set<Integer> fireworksToRemoveNextTick = new HashSet<>();

    public CompensatedFireworks(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        activeFireworks.removeAll(fireworksToRemoveNextTick);
        fireworksToRemoveNextTick.clear();
    }

    public boolean hasFirework(int entityId) {
        return activeFireworks.contains(entityId);
    }

    public void addNewFirework(int entityID) {
        activeFireworks.add(entityID);
    }

    public void removeFirework(int entityID) {
        if (activeFireworks.contains(entityID)) {
            fireworksToRemoveNextTick.add(entityID);
        }
    }

    public int getMaxFireworksAppliedPossible() {
        return activeFireworks.size();
    }
}
