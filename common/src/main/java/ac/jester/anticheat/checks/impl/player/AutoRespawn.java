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

/**
 * AutoRespawn — detects respawning within milliseconds of dying.
 *
 * In vanilla, the "Respawn" button only appears after the death screen loads
 * (~500ms+), and a human takes additional time to click it. AutoRespawn
 * listens for the death notification (UPDATE_HEALTH with health ≤ 0) and
 * immediately sends a CLIENT_STATUS PERFORM_RESPAWN packet — typically within
 * 1-3 ticks (50-150ms).
 *
 * Immediate-respawn gamerule: when doImmediateRespawn is true, the vanilla
 * client auto-sends PERFORM_RESPAWN instantly on death — every death would
 * false-flag. The server announces this via CHANGE_GAME_STATE with reason
 * ENABLE_RESPAWN_SCREEN (value 1 = respawn screen disabled). We track that
 * state and suspend the check while immediate respawn is active.
 *
 * Note: minRespawnMs is conservative (default 300ms) to account for high-ping
 * players whose death packet may be delayed. We also skip checks when
 * transactionPing is above a reasonable threshold.
 */
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
        // Initial gamerule state arrives in JOIN_GAME
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            WrapperPlayServerJoinGame join = new WrapperPlayServerJoinGame(event);
            immediateRespawn = !join.isRespawnScreenEnabled();
            return;
        }

        // Track the doImmediateRespawn gamerule announcement
        if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
            WrapperPlayServerChangeGameState state = new WrapperPlayServerChangeGameState(event);
            if (state.getReason() == WrapperPlayServerChangeGameState.Reason.ENABLE_RESPAWN_SCREEN) {
                // value 1 = immediate respawn enabled (respawn screen disabled)
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

        // Client auto-respawns instantly when doImmediateRespawn is on — not a cheat
        if (immediateRespawn) {
            deathTime = 0L;
            return;
        }

        if (deathTime == 0L) return;

        long elapsed = System.currentTimeMillis() - deathTime;
        deathTime = 0L;

        // Skip if high ping makes timing unreliable
        if (player.getTransactionPing() > 500) return;

        if (elapsed < minRespawnMs) {
            flagAndAlert(String.format("death->respawn=%dms min=%dms ping=%dms",
                    elapsed, minRespawnMs, player.getTransactionPing()));
        }
    }
}
