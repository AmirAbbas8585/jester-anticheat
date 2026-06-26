package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * Criticals — detects fake critical hit manipulation.
 *
 * A legitimate critical hit requires the player to be falling (not on ground).
 * Criticals hacks send a position packet claiming onGround=false (in air) right before
 * an attack to force a critical hit, while the server simulation says they are on ground.
 *
 * Detection: flag when an INTERACT_ENTITY ATTACK arrives and:
 *   - server-computed onGround is true (player is actually on ground)
 *   - client's last claimed ground state is false (client says in air → wants a crit)
 *
 * This overlap of a ground spoof coinciding with an attack is the Criticals signature.
 */
@CheckData(name = "Criticals", description = "Fake critical hit via ground spoof during attack", setback = 5)
public final class Criticals extends Check implements PacketCheck {

    private int maxPingMs = 500;

    public Criticals(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPingMs = config.getIntElse("Criticals.max-ping", 500);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        // Need reliable ticking before flagging
        if (!player.isTickingReliablyFor(5)) return;
        if (player.getTransactionPing() > maxPingMs) return;

        // Skip the exact tick of landing: client packet was in-flight while the server just
        // computed onGround=true. This is not a cheat — it is a timing artifact.
        if (!player.lastOnGround && player.onGround) return;

        // The Criticals signature: server says on ground, client claims air
        if (player.onGround && !player.clientClaimsLastOnGround) {
            flagAndAlert(String.format("onGround=%b clientClaims=%b ping=%dms",
                    player.onGround, player.clientClaimsLastOnGround, player.getTransactionPing()));
        }
    }
}
