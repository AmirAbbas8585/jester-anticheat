package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.Arrays;

/**
 * AutoClickerD — slow autoclickers, including the ones that randomise their delay.
 *
 * AutoClickerA analyses fast clicking: it throws away any gap over 2 seconds and
 * needs more than 6 CPS before it will even look. That makes it structurally
 * blind to a macro attacking every few seconds, and raising its clamp would not
 * help — its 20-sample window and CPS gate are built for a different regime.
 * This check owns the slow regime instead, over a long window.
 *
 * Better clickers deliberately jitter their interval to beat consistency tests,
 * so a plain standard-deviation test is not enough. Three statistics are used,
 * and they attack jitter from different directions:
 *
 *  1. COEFFICIENT OF VARIATION. A fixed macro sits near zero. Uniform jitter of
 *     +/-500ms around 4s still only reaches about 0.07. Humans aiming for "every
 *     few seconds" land around 0.25-0.50.
 *
 *  2. TAIL RATIO (max / median) — the strongest one, and the direct answer to
 *     jitter. Human timing has a long right tail: over several minutes a person
 *     WILL get distracted once and produce a gap several times the median.
 *     Randomised jitter is bounded by construction, so its maximum stays close
 *     to its median no matter how wide the randomisation is. Beating this test
 *     requires inserting real multi-second pauses, which costs farm throughput.
 *
 *  3. SYMMETRY. Uniform and gaussian jitter are symmetric about the median;
 *     human timing is right-skewed. Machine sits near 1.0, humans well above.
 *
 * Any TWO of the three must read as machine, on TWO consecutive windows, before
 * this flags — roughly eight minutes of evidence. That combination is what keeps
 * the false-positive rate down: each statistic alone has a plausible innocent
 * explanation, and the conjunction does not.
 *
 * Note the direction of the lag failure mode: server lag and ping spikes ADD
 * variance, which pushes every statistic toward "human". So lag here causes
 * missed detections, never false accusations, which is the safe direction.
 */
@CheckData(name = "AutoClickerD", configName = "AutoClickerD",
        description = "Slow or randomised autoclicker (long-window interval statistics)")
public final class AutoClickerD extends Check implements PacketCheck {

    private int windowSize = 60;
    private double maxCv = 0.12;
    private double maxTailRatio = 1.6;
    private double symmetryLow = 0.7;
    private double symmetryHigh = 1.3;
    private int minMachineStats = 2;
    private int minConsecutiveWindows = 2;
    private long minIntervalMs = 200;
    private long maxIntervalMs = 30_000;

    private long[] intervals = new long[60];
    private int count;
    private long lastAttackMs;
    private int machineWindows;

    public AutoClickerD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        int size = Math.max(20, config.getIntElse("AutoClickerD.window-size", 60));
        if (size != windowSize || intervals.length != size) {
            windowSize = size;
            intervals = new long[size];
            count = 0;
        }
        maxCv = config.getDoubleElse("AutoClickerD.max-cv", 0.12);
        maxTailRatio = config.getDoubleElse("AutoClickerD.max-tail-ratio", 1.6);
        symmetryLow = config.getDoubleElse("AutoClickerD.symmetry-low", 0.7);
        symmetryHigh = config.getDoubleElse("AutoClickerD.symmetry-high", 1.3);
        minMachineStats = Math.min(3, Math.max(1, config.getIntElse("AutoClickerD.min-machine-stats", 2)));
        minConsecutiveWindows = Math.max(1, config.getIntElse("AutoClickerD.min-consecutive-windows", 2));
        minIntervalMs = config.getIntElse("AutoClickerD.min-interval-ms", 200);
        maxIntervalMs = config.getIntElse("AutoClickerD.max-interval-ms", 30000);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        long now = System.currentTimeMillis();
        long previous = lastAttackMs;
        lastAttackMs = now;
        if (previous == 0) return;

        long interval = now - previous;
        // Outside this band it isn't the pattern we model: below is fast clicking
        // (AutoClickerA's job) and above is someone who simply stopped and came
        // back, which would otherwise inject a fake "human" long tail.
        if (interval < minIntervalMs || interval > maxIntervalMs) {
            count = 0;
            machineWindows = 0;
            return;
        }

        if (count < windowSize) {
            intervals[count++] = interval;
            if (count < windowSize) return;
        } else {
            System.arraycopy(intervals, 1, intervals, 0, windowSize - 1);
            intervals[windowSize - 1] = interval;
        }

        evaluateWindow();
        count = 0; // start a fresh, independent window
    }

    private void evaluateWindow() {
        long[] sorted = Arrays.copyOf(intervals, windowSize);
        Arrays.sort(sorted);

        double mean = 0;
        for (long v : intervals) mean += v;
        mean /= windowSize;
        if (mean <= 0) return;

        double variance = 0;
        for (long v : intervals) variance += (v - mean) * (v - mean);
        double cv = Math.sqrt(variance / windowSize) / mean;

        double median = percentile(sorted, 0.50);
        if (median <= 0) return;
        double tailRatio = sorted[windowSize - 1] / median;

        double upper = percentile(sorted, 0.90) - median;
        double lower = median - percentile(sorted, 0.10);
        // A perfectly flat window has no spread at all; treat that as maximally
        // machine-like rather than dividing by zero.
        double symmetry = lower <= 0 ? (upper <= 0 ? 1.0 : Double.MAX_VALUE) : upper / lower;

        int machineStats = 0;
        if (cv < maxCv) machineStats++;
        if (tailRatio < maxTailRatio) machineStats++;
        if (symmetry >= symmetryLow && symmetry <= symmetryHigh) machineStats++;

        if (machineStats < minMachineStats) {
            machineWindows = 0;
            return;
        }

        if (++machineWindows >= minConsecutiveWindows && player.isTickingReliablyFor(3)) {
            flagAndAlert(String.format(
                    "mean=%.0fms cv=%.3f tail=%.2f symmetry=%.2f stats=%d/3 windows=%d",
                    mean, cv, tailRatio, symmetry, machineStats, machineWindows));
            machineWindows = 0;
        }
    }

    private static double percentile(long[] sorted, double p) {
        int idx = (int) Math.round(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }
}
