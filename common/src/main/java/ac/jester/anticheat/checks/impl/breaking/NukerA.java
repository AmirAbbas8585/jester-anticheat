package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Nuker A — Multi-block simultaneous breaking detection.
 *
 * Vanilla players can only START breaking ONE block at a time.
 * A Nuker cheat sends START_DIGGING for multiple different block positions
 * in rapid succession (same tick or very close in time).
 *
 * We detect:
 *   1. Multiple distinct START_DIGGING positions in the same client tick — instant Nuker
 *   2. Abnormally high block break rate over a rolling window — rate-based Nuker
 *
 * Tick boundary: like AutoClickerB, this counts positions between the client's
 * own movement packets instead of a wall-clock window. TCP preserves packet
 * order, so network jitter that bunches several ticks of legitimate fast
 * instant-block breaking together (e.g. clearing grass/saplings) still delivers
 * the movement packets between them and can't inflate the per-tick count — a
 * 55ms wall-clock window false-flagged exactly this under jitter.
 *
 * Note: Players in Creative mode can instant-break blocks — we handle Creative separately
 * (different check logic for Creative vs Survival).
 */
@CheckData(name = "Nuker", configName = "NukerA",
        description = "Sending multiple block-break start packets simultaneously (Nuker/MultiBreak)")
public final class NukerA extends Check implements PacketCheck {

    // Distinct positions sent START_DIGGING in the current client tick
    private final Set<Long> diggingPositionsThisTick = new HashSet<>();

    // Rolling break rate: timestamps of FINISHED breaks
    private final ArrayDeque<Long> finishedBreaks = new ArrayDeque<>();
    // Configurable so servers with area-break enchants (e.g. CrazyEnchantments
    // Blast) — whose effect the anticheat can't read from packets — can raise the
    // ceiling instead of getting false Nuker flags. Defaults match vanilla survival.
    private int windowSeconds = 3;
    private int maxBreaksPerWindow = 15; // ~5 breaks/second max for survival
    private int maxSimultaneous = 3;     // distinct START positions in one tick

    // "Nuker" by definition breaks many DIFFERENT blocks rapidly. Repeatedly
    // re-attempting the SAME block position (e.g. a block a protection plugin
    // like WorldGuard keeps denying and resyncing back) is not nuking behavior
    // and must not count toward the rate limit, no matter how fast it's retried.
    private long lastFinishedPosHash = Long.MIN_VALUE;

    public NukerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBreaksPerWindow = config.getIntElse(getConfigName() + ".max-breaks-per-window", 15);
        windowSeconds = Math.max(1, config.getIntElse(getConfigName() + ".window-seconds", 3));
        maxSimultaneous = Math.max(2, config.getIntElse(getConfigName() + ".max-simultaneous", 3));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();

        // Client tick boundary — evaluate the window that just closed, same
        // pattern as AutoClickerB.
        if (isTickPacketIncludingNonMovement(type)) {
            diggingPositionsThisTick.clear();
            return;
        }

        if (type != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = digging.getAction();

        if (action == DiggingAction.START_DIGGING) {
            Vector3i pos = digging.getBlockPosition();
            long posHash = ((long) pos.getX() & 0x3FFFFFF) | (((long) pos.getZ() & 0x3FFFFFF) << 26) | ((long) pos.getY() << 52);
            diggingPositionsThisTick.add(posHash);

            if (diggingPositionsThisTick.size() >= maxSimultaneous) {
                flagAndAlert(String.format("simultaneous_starts=%d in one tick", diggingPositionsThisTick.size()));
                diggingPositionsThisTick.clear();
            }

        } else if (action == DiggingAction.FINISHED_DIGGING) {
            long now = System.currentTimeMillis();

            Vector3i finishedPos = digging.getBlockPosition();
            long finishedPosHash = ((long) finishedPos.getX() & 0x3FFFFFF) | (((long) finishedPos.getZ() & 0x3FFFFFF) << 26) | ((long) finishedPos.getY() << 52);
            boolean samePositionAsLast = finishedPosHash == lastFinishedPosHash;
            lastFinishedPosHash = finishedPosHash;

            if (samePositionAsLast) return; // repeatedly retrying the same block (e.g. a denied break) isn't nuking

            finishedBreaks.addLast(now);

            // Evict old entries
            long cutoff = now - (windowSeconds * 1000L);
            while (!finishedBreaks.isEmpty() && finishedBreaks.peekFirst() < cutoff) {
                finishedBreaks.pollFirst();
            }

            // Skip rate check for Creative mode (instant break is normal)
            if (player.gamemode == com.github.retrooper.packetevents.protocol.player.GameMode.CREATIVE) return;

            if (finishedBreaks.size() > maxBreaksPerWindow) {
                double rate = (double) finishedBreaks.size() / windowSeconds;
                flagAndAlert(String.format("rate=%.1f/s in %ds window", rate, windowSeconds));
                finishedBreaks.clear();
            }
        }
    }
}
