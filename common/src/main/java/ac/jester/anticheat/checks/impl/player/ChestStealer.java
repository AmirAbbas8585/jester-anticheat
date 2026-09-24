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

        int windowId = player.inventory.getOpenWindowID();
        if (windowId == 0) {
            resetState();
            return;
        }

        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);

        int slot = click.getSlot();
        if (slot == -1 || slot == -999) return;

        WrapperPlayClientClickWindow.WindowClickType clickType = click.getWindowClickType();
        if (clickType == WrapperPlayClientClickWindow.WindowClickType.QUICK_CRAFT
                || clickType == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL) {
            return;
        }

        long now = System.currentTimeMillis();

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
