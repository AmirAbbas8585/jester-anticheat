package ac.jester.anticheat.platform.api.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlatformPlayerCache {
    private static final PlatformPlayerCache INSTANCE = new PlatformPlayerCache();
    private final Map<UUID, PlatformPlayer> playerCache = new ConcurrentHashMap<>();

    private PlatformPlayerCache() {
    }

    public static PlatformPlayerCache getInstance() {
        return INSTANCE;
    }

    public PlatformPlayer addOrGetPlayer(UUID uuid, PlatformPlayer player) {
        return playerCache.compute(uuid, (key, existing) -> {
            if (existing != null) {
                return existing;
            }
            return player;
        });
    }

    public void removePlayer(UUID uuid) {
        playerCache.remove(uuid);
    }

    public PlatformPlayer getPlayer(UUID uuid) {
        return playerCache.get(uuid);
    }
}
