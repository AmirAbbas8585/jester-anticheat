package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * NoHitDelay — detects removing the minimum delay between attacks.
 *
 * Attacks are counted per CLIENT tick, bounded by the client's own movement
 * packets — never by wall-clock time. TCP jitter routinely delivers packets
 * from two adjacent ticks back-to-back, so measuring milliseconds between
 * attacks false flags normal double-clicks; the tick boundary is immune to
 * that because the movement packets arrive between the attacks in order.
 *
 * A vanilla client can plausibly produce 2 attacks inside one tick (two
 * mouse press events processed in the same tick on high-FPS clients), so we
 * require maxPerTick (default 3) attacks within a single client tick, on
 * minConsecutive consecutive occurrences, before flagging.
 */
@CheckData(name = "NoHitDelay", description = "Multiple attack packets within a single client tick")
public final class NoHitDelay extends Check implements PacketCheck {

    private int maxPerTick = 3;
    private int minConsecutive = 2;

    private int attacksThisTick = 0;
    private int consecutiveBadTicks = 0;

    public NoHitDelay(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPerTick = config.getIntElse("NoHitDelay.max-per-tick", 3);
        minConsecutive = config.getIntElse("NoHitDelay.min-consecutive", 2);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Attack cooldown system only exists in 1.9+
        if (!PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) return;

        // Client tick boundary — evaluate the window that just closed
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            if (attacksThisTick >= maxPerTick) {
                consecutiveBadTicks++;
                if (consecutiveBadTicks >= minConsecutive && player.isTickingReliablyFor(3)) {
                    flagAndAlert(String.format("attacks=%d/tick max=%d consecutive=%d ping=%dms",
                            attacksThisTick, maxPerTick, consecutiveBadTicks, player.getTransactionPing()));
                    consecutiveBadTicks = 0;
                }
            } else if (attacksThisTick > 0) {
                consecutiveBadTicks = 0;
            }
            attacksThisTick = 0;
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        attacksThisTick++;
    }
}
