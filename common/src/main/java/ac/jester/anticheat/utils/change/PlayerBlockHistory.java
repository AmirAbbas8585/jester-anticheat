package ac.jester.anticheat.utils.change;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;

public class PlayerBlockHistory {
    private final ConcurrentLinkedDeque<BlockModification> blockHistory = new ConcurrentLinkedDeque<>();

    public void add(BlockModification modification) {
        blockHistory.add(modification);
    }

    public Iterable<BlockModification> getRecentModifications(Predicate<BlockModification> filter) {
        return blockHistory.stream().filter(filter).toList();
    }

    public void cleanup(int maxTick) {
        while (!blockHistory.isEmpty() && maxTick - blockHistory.peekFirst().tick() > 0) {
            blockHistory.pollFirst();
        }
    }

    public int size() {
        return blockHistory.size();
    }

    public void clear() {
        blockHistory.clear();
    }
}
