package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PacketHidePlayerInfo extends PacketListenerAbstract {
    public PacketHidePlayerInfo() {
        super(PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_12_2))
                return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());

            if (receiver == null) {
                return;
            }

            WrapperPlayServerPlayerInfo info = new WrapperPlayServerPlayerInfo(event);

            if (info.getAction() == WrapperPlayServerPlayerInfo.Action.UPDATE_GAME_MODE || info.getAction() == WrapperPlayServerPlayerInfo.Action.ADD_PLAYER) {
                List<WrapperPlayServerPlayerInfo.PlayerData> nmsPlayerInfoDataList = info.getPlayerDataList();

                int hideCount = 0;
                for (WrapperPlayServerPlayerInfo.PlayerData playerData : nmsPlayerInfoDataList) {
                    if (GrimAPI.INSTANCE.getSpectateManager().shouldHidePlayer(receiver, playerData)) {
                        hideCount++;
                        if (playerData.getGameMode() == GameMode.SPECTATOR)
                            playerData.setGameMode(GameMode.SURVIVAL);
                    }
                }

                if (hideCount == nmsPlayerInfoDataList.size() && info.getAction() == WrapperPlayServerPlayerInfo.Action.UPDATE_GAME_MODE) {
                    event.setCancelled(true);
                } else if (hideCount > 0) {
                    event.markForReEncode(true);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;
            WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
            EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = wrapper.getActions();
            if (actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE)) {
                boolean onlyGameMode = actions.size() == 1;
                int hideCount = 0;
                List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> modified = new ArrayList<>(wrapper.getEntries().size());
                for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : wrapper.getEntries()) {
                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo modifiedPacket = null;
                    final UserProfile gameProfile = entry.getGameProfile();
                    if (GrimAPI.INSTANCE.getSpectateManager().shouldHidePlayer(receiver, gameProfile.getUUID())) {
                        hideCount++;
                        if (entry.getGameMode() == GameMode.SPECTATOR) {
                            modifiedPacket = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                    gameProfile,
                                    entry.isListed(),
                                    entry.getLatency(),
                                    GameMode.SURVIVAL,
                                    entry.getDisplayName(),
                                    entry.getChatSession()
                            );
                            modified.add(modifiedPacket);
                        }
                    }

                    if (modifiedPacket == null) {
                        modified.add(entry);
                    } else if (!onlyGameMode) {
                        modified.add(modifiedPacket);
                    }
                }

                if (hideCount == modified.size()) {
                    if (onlyGameMode) {
                        event.setCancelled(true);
                    } else {
                        wrapper.getActions().remove(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE);
                        event.markForReEncode(true);
                    }
                } else {
                    wrapper.setEntries(modified);
                    event.markForReEncode(true);
                }
            }
        }
    }
}
