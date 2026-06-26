package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;


/**
 * AutoClicker B — Multi-click-per-tick detection.
 *
 * In vanilla, a player can only click once per client tick, so at most one
 * combat swing is sent between two consecutive movement packets. A macro or
 * packet-spammer can exceed that.
 *
 * Tick boundary: the client's own movement packet (sent once per tick). We
 * count ANIMATION packets between movement packets instead of using a wall
 * clock — TCP preserves packet order, so network jitter that bunches several
 * ticks together also delivers the movement packets between the swings and
 * cannot inflate the per-tick count (the old 50ms wall-clock window false
 * flagged every player with minor jitter).
 *
 * Digging exclusion: mining sends one swing per tick too; those are filtered
 * by the digging state so left-click-held mining never contributes.
 */
@CheckData(name = "AutoClicker", configName = "AutoClickerB",
        description = "Multiple swing packets sent within a single client tick")
public final class AutoClickerB extends Check implements PacketCheck {

    private int animationsThisTick = 0;
    private boolean digging = false;

    // A single isolated multi-swing-in-one-tick burst is not how a macro looks —
    // a real macro does this on nearly every tick it's active. One coincidental
    // burst (rare protocol/network edge case) only nudges this counter; only
    // sustained recent bursts actually flag. Real-world reports of a chronically
    // jittery connection (PojavLauncher mobile network) still slowly built up
    // enough violations to get kicked at 2-in-a-row — raised to 4, and the
    // counter resets after a flag instead of continuing to chain, so a flaky
    // connection has to keep proving the pattern instead of riding one streak.
    private int recentBursts = 0;
    private long lastBurstTime = 0L;
    private long burstWindowMs = 4000L;
    private int minBursts = 4;

    // Cancel-before-kick: see AutoClickerA. While the dead window is open the
    // player's attacks are cancelled so the macro does nothing; if they keep
    // bursting they still climb to max-violations and get kicked.
    private boolean cancelBeforeKick = true;
    private long cancelDurationMs = 3000L;
    private long cancelUntil = 0L;

    public AutoClickerB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minBursts = config.getIntElse("AutoClickerB.min-bursts", 4);
        burstWindowMs = config.getIntElse("AutoClickerB.burst-window-ms", 4000);
        cancelBeforeKick = config.getBooleanElse("AutoClickerB.cancel-before-kick", true);
        cancelDurationMs = config.getIntElse("AutoClickerB.cancel-duration-ms", 3000);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();

        // Cancel-before-kick dead window: silently drop the player's attacks.
        if (cancelBeforeKick && type == PacketType.Play.Client.INTERACT_ENTITY
                && System.currentTimeMillis() < cancelUntil
                && shouldModifyPackets()) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return;
        }

        // Client tick boundary — reset the per-tick swing counter
        if (isTickPacketIncludingNonMovement(type)) {
            animationsThisTick = 0;
            return;
        }

        // Track digging — mining swings are vanilla 1/tick but excluded anyway
        if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging dig =
                    new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging(event);
            switch (dig.getAction()) {
                case START_DIGGING -> digging = true;
                case CANCELLED_DIGGING, FINISHED_DIGGING -> digging = false;
                default -> { }
            }
            return;
        }

        if (type != PacketType.Play.Client.ANIMATION) return;
        if (digging) return;

        animationsThisTick++;

        if (animationsThisTick >= 3 && player.isTickingReliablyFor(3)) {
            long now = System.currentTimeMillis();
            recentBursts = (now - lastBurstTime < burstWindowMs) ? recentBursts + 1 : 1;
            lastBurstTime = now;

            if (recentBursts >= minBursts) {
                if (flagAndAlert("anims=" + animationsThisTick + " in one tick ping=" + player.getTransactionPing() + "ms consecutive=" + recentBursts)
                        && cancelBeforeKick) {
                    cancelUntil = now + cancelDurationMs;
                }
                recentBursts = 0;
            }
            animationsThisTick = 0;
        }
    }
}
