package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * MultiInteractB — right-click interacting with multiple DISTINCT entities in a
 * single client tick (right-click / interact aura).
 *
 * Vanilla can only interact with one entity per tick. The old version compared
 * the sub-entity hit VECTOR (getTarget) between packets, which is noisy: a
 * single right-click sends both INTERACT_AT and INTERACT, and fast clicking the
 * same entity with a moving cursor yields slightly different vectors → false
 * positives. This version tracks distinct ENTITY IDs (ATTACK excluded — that's
 * KillAuraB's job) within a client tick and requires the anomaly on consecutive
 * ticks so network jitter bunching two ticks together can't trip it.
 */
@CheckData(name = "MultiInteractB", experimental = true,
        description = "Right-click interacting with multiple distinct entities in one tick")
public class MultiInteractB extends Check implements PostPredictionCheck {

    private final Set<Integer> entitiesThisTick = new HashSet<>();
    private int consecutiveBadTicks = 0;
    private static final int MIN_CONSECUTIVE = 2;

    public MultiInteractB(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Client tick boundary — evaluate the window that just closed
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            if (entitiesThisTick.size() >= 2) {
                consecutiveBadTicks++;
                if (consecutiveBadTicks >= MIN_CONSECUTIVE && player.isTickingReliablyFor(3)) {
                    flagAndAlert("entities=" + entitiesThisTick.size()
                            + " consecutive=" + consecutiveBadTicks);
                    consecutiveBadTicks = 0;
                }
            } else if (!entitiesThisTick.isEmpty()) {
                consecutiveBadTicks = 0;
            }
            entitiesThisTick.clear();
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        if (!player.cameraEntity.isSelf()) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        // ATTACK on multiple entities is KillAuraB; this targets right-click aura
        if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        entitiesThisTick.add(interact.getEntityId());
    }
}
