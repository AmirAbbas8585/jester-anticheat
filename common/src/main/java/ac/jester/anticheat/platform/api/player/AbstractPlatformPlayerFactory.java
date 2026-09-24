package ac.jester.anticheat.platform.api.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractPlatformPlayerFactory<T> implements PlatformPlayerFactory {
    protected final PlatformPlayerCache cache = PlatformPlayerCache.getInstance();

    @Override
    public final @Nullable PlatformPlayer getFromUUID(@NotNull UUID uuid) {
        PlatformPlayer cachedPlayer = cache.getPlayer(uuid);
        if (cachedPlayer != null) {
            return cachedPlayer;
        }

        T nativePlayer = getNativePlayer(uuid);
        if (nativePlayer == null) {
            return null;
        }

        PlatformPlayer platformPlayer = createPlatformPlayer(nativePlayer);
        return cache.addOrGetPlayer(uuid, platformPlayer);
    }

    @Override
    public @Nullable PlatformPlayer getFromName(@NotNull String name) {
        T nativePlayer = getNativePlayer(name);
        if (nativePlayer == null) {
            return null;
        }

        PlatformPlayer platformPlayer = createPlatformPlayer(nativePlayer);
        return cache.addOrGetPlayer(platformPlayer.getUniqueId(), platformPlayer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final PlatformPlayer getFromNativePlayerType(@NotNull Object playerObject) {
        T nativePlayer = (T) Objects.requireNonNull(playerObject);
        UUID uuid = getPlayerUUID(nativePlayer);

        PlatformPlayer cachedPlayer = cache.getPlayer(uuid);
        if (cachedPlayer != null) {
            return cachedPlayer;
        }

        PlatformPlayer platformPlayer = createPlatformPlayer(nativePlayer);
        return cache.addOrGetPlayer(uuid, platformPlayer);
    }

    @Override
    public final void invalidatePlayer(@NotNull UUID uuid) {
        cache.removePlayer(uuid);
    }

    @Override
    public Collection<PlatformPlayer> getOnlinePlayers() {
        Collection<T> nativePlayers = getNativeOnlinePlayers();

        List<PlatformPlayer> platformPlayers = new ArrayList<>(nativePlayers.size());

        for (T nativePlayer : nativePlayers) {
            platformPlayers.add(getFromNativePlayerType(nativePlayer));
        }

        return platformPlayers;
    }

    public void replaceNativePlayer(@NotNull UUID uuid, @NotNull T player) {}

    protected abstract T getNativePlayer(@NotNull UUID uuid);

    protected abstract T getNativePlayer(@NotNull String name);

    protected abstract PlatformPlayer createPlatformPlayer(@NotNull T nativePlayer);

    protected abstract UUID getPlayerUUID(@NotNull T nativePlayer);

    protected abstract Collection<T> getNativeOnlinePlayers();

    @Override
    public abstract OfflinePlatformPlayer getOfflineFromUUID(@NotNull UUID uuid);

    @Override
    public abstract OfflinePlatformPlayer getOfflineFromName(@NotNull String name);
}
