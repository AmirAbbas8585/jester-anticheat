package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * KillAura B — Multi-entity attack detection.
 *
 * In vanilla, a player can only attack ONE entity per tick.
 * KillAura cheats attack multiple entities in rapid succession within a single tick.
 *
 * We track distinct entity IDs attacked within one CLIENT tick — bounded by
 * the client's own movement packets, not a wall clock. TCP preserves packet
 * order, so network jitter that bunches several ticks together also delivers
 * the movement packets between the attacks and cannot merge two ticks into
 * one window (the old 50ms wall-clock window false flagged spam-clicking
 * through a crowd of mobs under minor jitter).
 *
 * False positive prevention:
 *   - AOE effects (Sweeping Edge on 1.9+) send damage to multiple entities,
 *     but the INTERACT_ENTITY ATTACK packet is only sent for the primary target.
 *     Secondary sweep damage is server-side — no additional INTERACT_ENTITY packets.
 *   - This check is therefore safe even on servers with sweep mechanics.
 */
@CheckData(name = "KillAura", configName = "KillAuraB",
        description = "Attacking multiple distinct entities within a single client tick")
public final class KillAuraB extends Check implements PacketCheck {

    private final Set<Integer> entitiesThisTick = new HashSet<>();

    public KillAuraB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Client tick boundary — new attack window
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            entitiesThisTick.clear();
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        entitiesThisTick.add(interact.getEntityId());

        if (entitiesThisTick.size() >= 2 && player.isTickingReliablyFor(3)) {
            flagAndAlert(String.format("targets=%d ping=%dms",
                    entitiesThisTick.size(), player.getTransactionPing()));
            entitiesThisTick.clear();
        }
    }
}
