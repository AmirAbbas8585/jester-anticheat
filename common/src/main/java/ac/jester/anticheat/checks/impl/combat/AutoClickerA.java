package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;

/**
 * AutoClicker A — Statistical consistency analysis.
 *
 * Real human click intervals have high variance (humans cannot click at exactly
 * the same rate consistently). Auto-clickers produce extremely consistent intervals.
 *
 * We collect a rolling window of click intervals and compute the coefficient of
 * variation (CV = stddev / mean). Very low CV indicates an auto-clicker.
 *
 * Also checks for sustained super-human CPS (>20 CPS is physically impossible).
 *
 * CRITICAL exclusion — block digging: while a player holds left-click to mine,
 * the client sends one ANIMATION packet per tick (exactly 50ms apart, CV ≈ 0).
 * Counting those as "clicks" makes every mining player look like a perfect
 * auto-clicker. We track START/CANCELLED/FINISHED_DIGGING and ignore all swings
 * during digging (plus a short grace period after), clearing the window.
 */
@CheckData(name = "AutoClicker", configName = "AutoClickerA",
        description = "Statistically consistent click intervals indicating AutoClicker")
public final class AutoClickerA extends Check implements PacketCheck {

    private int windowSize = 20;
    private double maxCpsHuman = 20.0;
    private double minCvHuman = 0.10;

    // Cancel-before-kick: when the player is detected, their attack packets are
    // silently cancelled for a few seconds (their clicks land on nothing) before
    // any kick. A legit borderline clicker stops and decays; a real macro keeps
    // clicking through the dead window, keeps flagging and eventually gets kicked
    // at max-violations. cancelUntil is the wall-clock time the dead window ends.
    private boolean cancelBeforeKick = true;
    private long cancelDurationMs = 3000L;
    private long cancelUntil = 0L;
    // A real log showed cps=20.1 mean=49.8ms — averaged over a full 20-click
    // window, i.e. a player clicking essentially exactly once per tick (the
    // genuine physical/vanilla maximum), pushed barely over 20.0 by ordinary
    // packet-timestamp jitter, not an extra-fast click. Require this to recur
    // on the NEXT window evaluation too before treating it as a violation —
    // a sustained macro stays over the line on every window, a borderline
    // human at the edge of 20 CPS does not.
    private int consecutiveCpsOver = 0;
    private int minConsecutiveCps = 2;

    private ArrayDeque<Long> clickTimes = new ArrayDeque<>(windowSize + 1);
    private long lastClickTime = 0L;
    private volatile double lastCps = 0.0;

    // Digging state: swings during (and right after) mining are NOT clicks
    private boolean digging = false;
    private long lastDigPacketTime = 0L;
    private static final long DIG_GRACE_MS = 500L;

    public AutoClickerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        windowSize = config.getIntElse("AutoClickerA.window-size", 20);
        maxCpsHuman = config.getDoubleElse("AutoClickerA.max-cps", 20.0);
        minCvHuman = config.getDoubleElse("AutoClickerA.min-cv", 0.10);
        cancelBeforeKick = config.getBooleanElse("AutoClickerA.cancel-before-kick", true);
        cancelDurationMs = config.getIntElse("AutoClickerA.cancel-duration-ms", 3000);
        minConsecutiveCps = config.getIntElse("AutoClickerA.min-consecutive-cps", 2);
        clickTimes = new ArrayDeque<>(windowSize + 1);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Cancel-before-kick dead window: silently drop the player's attacks so
        // the auto-clicker does nothing, without removing them from the server.
        if (cancelBeforeKick
                && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                && System.currentTimeMillis() < cancelUntil
                && shouldModifyPackets()) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return;
        }

        // Track digging state — mining swings must never count as clicks
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging1 = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = digging1.getAction();
            if (action == DiggingAction.START_DIGGING) {
                digging = true;
                lastDigPacketTime = System.currentTimeMillis();
                clickTimes.clear();
                lastClickTime = 0L;
            } else if (action == DiggingAction.CANCELLED_DIGGING || action == DiggingAction.FINISHED_DIGGING) {
                digging = false;
                lastDigPacketTime = System.currentTimeMillis();
                clickTimes.clear();
                lastClickTime = 0L;
            }
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) return;

        long now = System.currentTimeMillis();

        // Ignore swings while digging or within the post-digging grace period
        if (digging || now - lastDigPacketTime < DIG_GRACE_MS) {
            clickTimes.clear();
            lastClickTime = 0L;
            return;
        }

        if (lastClickTime != 0L) {
            long interval = now - lastClickTime;
            // Sanity: ignore suspiciously large gaps (> 2s = player stopped clicking)
            if (interval > 2000L) {
                clickTimes.clear();
                lastClickTime = now;
                return;
            }

            clickTimes.addLast(interval);
            if (clickTimes.size() > windowSize) {
                clickTimes.pollFirst();
            }

            if (clickTimes.size() >= windowSize) {
                double mean = clickTimes.stream().mapToLong(Long::longValue).average().orElse(0);
                if (mean < 1) {
                    lastClickTime = now;
                    return;
                }

                // CPS from mean interval
                double cps = 1000.0 / mean;
                lastCps = cps;

                // Check for impossible CPS
                if (cps > maxCpsHuman) {
                    consecutiveCpsOver++;
                    if (consecutiveCpsOver >= minConsecutiveCps
                            && flagAndAlert(String.format("cps=%.1f mean=%.1fms consecutive=%d", cps, mean, consecutiveCpsOver))
                            && cancelBeforeKick) {
                        cancelUntil = now + cancelDurationMs;
                    }
                    lastClickTime = now;
                    return;
                }
                consecutiveCpsOver = 0;

                // Coefficient of variation — autoclickers have very low variance
                double variance = clickTimes.stream()
                        .mapToDouble(t -> (t - mean) * (t - mean))
                        .average().orElse(0);
                double stddev = Math.sqrt(variance);
                double cv = stddev / mean;

                if (cv < minCvHuman && cps > 6.0
                        && flagAndAlert(String.format("cv=%.3f cps=%.1f mean=%.1fms", cv, cps, mean))
                        && cancelBeforeKick) {
                    cancelUntil = now + cancelDurationMs;
                }
            }
        }

        lastClickTime = now;
    }

    public double getLastCps() { return lastCps; }
}
