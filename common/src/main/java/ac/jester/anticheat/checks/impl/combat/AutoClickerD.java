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
        count = 0;
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
