package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;

import java.util.ArrayDeque;

/**
 * ChestStealer — detects taking items from a container at superhuman speed.
 *
 * ChestStealer hacks automatically click every slot in a chest/container,
 * sending a burst of CLICK_WINDOW packets far faster than any human can click.
 *
 * A human can click at most 15-20 times per second. ChestStealer typically
 * sends 10-20 inventory clicks in under 100ms (100+ clicks/sec).
 *
 * Detection A: More than maxClicks CLICK_WINDOW packets in 1 second while a
 *              non-player container is open.
 *
 * Detection B: Multiple consecutive clicks with < minIntervalMs spacing.
 *              Humans physically cannot click faster than ~50ms intervals
 *              consistently in a real inventory UI.
 */
@CheckData(name = "ChestStealer", description = "Clicking container slots at superhuman speed")
public final class ChestStealer extends Check implements PacketCheck {

    private int maxCps = 22;
    private int minIntervalMs = 40;
    private int burstThreshold = 8;

    private final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
    private long lastClickTime = 0L;
    private int consecutiveFastClicks = 0;

    public ChestStealer(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxCps = config.getIntElse("ChestStealer.max-cps", 22);
        minIntervalMs = config.getIntElse("ChestStealer.min-interval-ms", 40);
        burstThreshold = config.getIntElse("ChestStealer.burst-threshold", 8);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;

        // Only flag when an external container is open (windowId != 0 = non-player inventory)
        int windowId = player.inventory.getOpenWindowID();
        if (windowId == 0) {
            resetState();
            return;
        }

        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);

        // Sanity: only check actual item-move actions (not drag/drop outside)
        int slot = click.getSlot();
        if (slot == -1 || slot == -999) return;

        // Drag-painting (QUICK_CRAFT) sends one packet per slot in a single tick,
        // and double-click collect (PICKUP_ALL) sends multiple packets per click —
        // both produce legit sub-40ms bursts and must not count
        WrapperPlayClientClickWindow.WindowClickType clickType = click.getWindowClickType();
        if (clickType == WrapperPlayClientClickWindow.WindowClickType.QUICK_CRAFT
                || clickType == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL) {
            return;
        }

        long now = System.currentTimeMillis();

        // Detection B: burst of rapid consecutive clicks
        if (lastClickTime != 0L) {
            long interval = now - lastClickTime;
            if (interval < minIntervalMs) {
                consecutiveFastClicks++;
                if (consecutiveFastClicks >= burstThreshold && player.isTickingReliablyFor(3)) {
                    flagAndAlert(String.format("burst=%d interval=%dms min=%dms",
                            consecutiveFastClicks, interval, minIntervalMs));
                    consecutiveFastClicks = 0;
                }
            } else {
                consecutiveFastClicks = 0;
            }
        }
        lastClickTime = now;

        // Detection A: click rate per second
        clickTimes.addLast(now);
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000L) {
            clickTimes.pollFirst();
        }

        int cps = clickTimes.size();
        if (cps > maxCps && player.isTickingReliablyFor(3)) {
            if (flagAndAlert(String.format("cps=%d max=%d window=%d", cps, maxCps, windowId))) {
                clickTimes.clear();
                consecutiveFastClicks = 0;
            }
        }
    }

    private void resetState() {
        clickTimes.clear();
        lastClickTime = 0L;
        consecutiveFastClicks = 0;
    }
}
