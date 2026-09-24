package ac.jester.anticheat.utils.anticheat;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.event.events.GrimJoinEvent;
import ac.grim.grimac.api.event.events.GrimQuitEvent;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    public final Collection<User> exemptUsers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<User, GrimPlayer> playerDataMap = new ConcurrentHashMap<>();

    @Nullable
    public GrimPlayer getPlayer(final @NotNull UUID uuid) {
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(uuid);
        User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        return getPlayer(user);
    }

    @Nullable
    public GrimPlayer getPlayer(final @NotNull User user) {
        @Nullable GrimPlayer player = playerDataMap.get(user);
        if (player != null && player.platformPlayer != null && player.platformPlayer.isExternalPlayer())
            return null;
        return player;
    }

    public boolean shouldCheck(@NotNull User user) {
        if (exemptUsers.contains(user)) return false;
        if (!ChannelHelper.isOpen(user.getChannel())) return false;

        if (user.getUUID() != null) {
            GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if (grimPlayer != null && grimPlayer.hasPermission("jester.exempt")) {
                exemptUsers.add(user);
                return false;
            }
        }

        return true;
    }

    public void addUser(final @NotNull User user) {
        if (shouldCheck(user)) {
            GrimPlayer player = new GrimPlayer(user);
            playerDataMap.put(user, player);
            GrimAPI.INSTANCE.getEventBus().post(new GrimJoinEvent(player));
        }
    }

    public GrimPlayer remove(final @NotNull User user) {
        return playerDataMap.remove(user);
    }

    public void onDisconnect(User user) {
        GrimPlayer grimPlayer = remove(user);
        if (grimPlayer != null) GrimAPI.INSTANCE.getEventBus().post(new GrimQuitEvent(grimPlayer));
        exemptUsers.remove(user);

        UUID uuid = user.getProfile().getUUID();

        if (uuid == null)
            return;

        GrimAPI.INSTANCE.getAlertManager().handlePlayerQuit(
                GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromUUID(uuid)
        );

        GrimAPI.INSTANCE.getAlertRateLimiter().removePlayer(uuid);

        ac.jester.anticheat.database.DatabaseManager.onPlayerQuit(uuid);

        GrimAPI.INSTANCE.getSpectateManager().onQuit(uuid);

        GrimAPI.INSTANCE.getPlatformPlayerFactory().invalidatePlayer(uuid);
    }

    public Collection<GrimPlayer> getEntries() {
        return playerDataMap.values();
    }

    public int size() {
        return playerDataMap.size();
    }
}
