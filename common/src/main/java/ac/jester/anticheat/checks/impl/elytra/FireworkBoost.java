package ac.jester.anticheat.checks.impl.elytra;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

import java.util.ArrayDeque;

/**
 * FireworkBoost — macro/automated firework boosting while gliding.
 *
 * Spam right-clicking rockets mid-flight is legitimate, so a rate limit
 * would false flag. What no human produces is machine-uniform timing:
 * we collect the intervals between firework uses while gliding and flag
 * when the coefficient of variation (stddev/mean) over a full window is
 * below the threshold — human spam clicking stays well above it, macros
 * and ElytraFly firework modes sit near zero.
 */
@CheckData(name = "FireworkBoost", configName = "FireworkBoost",
        description = "Machine-uniform firework boost timing while gliding (macro)")
public final class FireworkBoost extends Check implements PacketCheck {

    private int windowSize = 8;
    private double maxCv = 0.06;
    private long maxIntervalMs = 1200;

    private final ArrayDeque<Long> useTimes = new ArrayDeque<>();

    public FireworkBoost(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        windowSize = config.getIntElse("FireworkBoost.window-size", 8);
        maxCv = config.getDoubleElse("FireworkBoost.max-cv", 0.06);
        maxIntervalMs = config.getLongElse("FireworkBoost.max-interval-ms", 1200);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (!player.isGliding) {
            useTimes.clear();
            return;
        }

        WrapperPlayClientUseItem use = new WrapperPlayClientUseItem(event);
        ItemStack item = use.getHand() == InteractionHand.OFF_HAND
                ? player.inventory.getOffHand()
                : player.inventory.getHeldItem();
        if (item == null || !item.is(ItemTypes.FIREWORK_ROCKET)) return;

        long now = System.currentTimeMillis();
        useTimes.addLast(now);
        if (useTimes.size() > windowSize + 1) useTimes.pollFirst();
        if (useTimes.size() < windowSize + 1) return;

        // Build intervals; a long pause means separate boost bursts — restart
        Long[] times = useTimes.toArray(new Long[0]);
        double[] intervals = new double[times.length - 1];
        double sum = 0;
        for (int i = 1; i < times.length; i++) {
            long interval = times[i] - times[i - 1];
            if (interval > maxIntervalMs) {
                useTimes.clear();
                useTimes.addLast(now);
                return;
            }
            intervals[i - 1] = interval;
            sum += interval;
        }

        double mean = sum / intervals.length;
        if (mean <= 0) return;
        double variance = 0;
        for (double interval : intervals) {
            variance += (interval - mean) * (interval - mean);
        }
        double cv = Math.sqrt(variance / intervals.length) / mean;

        if (cv < maxCv && player.isTickingReliablyFor(5)) {
            flagAndAlert(String.format("cv=%.4f max=%.2f mean=%.0fms boosts=%d ping=%dms",
                    cv, maxCv, mean, windowSize, player.getTransactionPing()));
            useTimes.clear();
        }
    }
}
