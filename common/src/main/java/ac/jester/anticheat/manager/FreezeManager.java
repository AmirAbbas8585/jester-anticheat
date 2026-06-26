package ac.jester.anticheat.manager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages staff-frozen players.
 * Frozen players cannot move; any position packet is bounced back.
 * Implemented by the platform (Bukkit FreezeListener) which actually enforces it.
 */
public final class FreezeManager {

    // UUID -> frozen-by staff UUID (null if frozen by console)
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();

    public boolean freeze(UUID target, UUID frozenBy) {
        if (frozenPlayers.containsKey(target)) return false;
        frozenPlayers.put(target, frozenBy != null ? frozenBy : target);
        return true;
    }

    public boolean unfreeze(UUID target) {
        return frozenPlayers.remove(target) != null;
    }

    public boolean isFrozen(UUID target) {
        return frozenPlayers.containsKey(target);
    }

    /** Who froze this player. Returns null if not frozen or frozen by console. */
    public UUID getFrozenBy(UUID target) {
        UUID by = frozenPlayers.get(target);
        return (by != null && !by.equals(target)) ? by : null;
    }

    public Set<UUID> getFrozenPlayers() {
        return Collections.unmodifiableSet(frozenPlayers.keySet());
    }

    public void removeAll() {
        frozenPlayers.clear();
    }
}
