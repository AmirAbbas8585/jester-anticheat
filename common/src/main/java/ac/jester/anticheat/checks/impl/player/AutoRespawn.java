package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;

@CheckData(name = "AutoRespawn", description = "Sending respawn packet immediately after death")
public final class AutoRespawn extends Check implements PacketCheck {
    private long deathTime = 0L;
    private int minRespawnMs = 300;
    private boolean immediateRespawn = false;

    public AutoRespawn(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minRespawnMs = config.getIntElse("AutoRespawn.min-respawn-ms", 300);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            WrapperPlayServerJoinGame join = new WrapperPlayServerJoinGame(event);
            immediateRespawn = !join.isRespawnScreenEnabled();
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
            WrapperPlayServerChangeGameState state = new WrapperPlayServerChangeGameState(event);
            if (state.getReason() == WrapperPlayServerChangeGameState.Reason.ENABLE_RESPAWN_SCREEN) {
                immediateRespawn = state.getValue() >= 1;
            }
            return;
        }

        if (event.getPacketType() != PacketType.Play.Server.UPDATE_HEALTH) return;

        WrapperPlayServerUpdateHealth health = new WrapperPlayServerUpdateHealth(event);
        if (health.getHealth() <= 0) {
            deathTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLIENT_STATUS) return;

        WrapperPlayClientClientStatus status = new WrapperPlayClientClientStatus(event);
        if (status.getAction() != WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN) return;

        if (immediateRespawn) {
            deathTime = 0L;
            return;
        }

        if (deathTime == 0L) return;

        long elapsed = System.currentTimeMillis() - deathTime;
        deathTime = 0L;

        if (player.getTransactionPing() > 500) return;

        if (elapsed < minRespawnMs) {
            flagAndAlert(String.format("death->respawn=%dms min=%dms ping=%dms",
                    elapsed, minRespawnMs, player.getTransactionPing()));
        }
    }
}
