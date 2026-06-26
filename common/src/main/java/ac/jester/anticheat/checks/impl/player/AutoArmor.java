package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;

/**
 * AutoArmor — detects automatically equipping better armor pieces.
 *
 * AutoArmor scans the player's inventory for better armor and immediately
 * sends CLICK_WINDOW packets to equip it — often within a single tick (50ms)
 * and without the inventory screen even being visually open.
 *
 * Armor slots in the player inventory window (windowId = 0):
 *   Slot 5 = Helmet, Slot 6 = Chestplate, Slot 7 = Leggings, Slot 8 = Boots
 *
 * Detection: clicks on 2+ DISTINCT armor slots within a very short window.
 *   No human can move the cursor between two different armor slots and click
 *   both in under 250ms. Distinct slots are required because a double-click
 *   on a single slot legitimately sends multiple CLICK_WINDOW packets within
 *   ~100ms (pickup + collect-to-cursor) — counting raw clicks would false
 *   flag every double-click.
 */
@CheckData(name = "AutoArmor", description = "Automatically equipping armor at superhuman speed")
public final class AutoArmor extends Check implements PacketCheck {

    private static final int SLOT_HELMET = 5;
    private static final int SLOT_BOOTS = 8;

    private int maxBurstMs = 250;
    private int burstThreshold = 2;

    // Bitmask of distinct armor slots (bits 0-3 = slots 5-8) clicked in window
    private int slotsClickedMask = 0;
    private long windowStart = 0L;

    public AutoArmor(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBurstMs = config.getIntElse("AutoArmor.max-burst-ms", 250);
        burstThreshold = config.getIntElse("AutoArmor.burst-threshold", 2);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;

        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);

        // Only check player's own inventory (windowId = 0)
        if (click.getWindowId() != 0) return;

        int slot = click.getSlot();
        // Armor slots 5-8 in player inventory window
        if (slot < SLOT_HELMET || slot > SLOT_BOOTS) return;

        long now = System.currentTimeMillis();

        if (now - windowStart > maxBurstMs) {
            slotsClickedMask = 0;
            windowStart = now;
        }

        slotsClickedMask |= 1 << (slot - SLOT_HELMET);
        int distinctSlots = Integer.bitCount(slotsClickedMask);

        if (distinctSlots >= burstThreshold && player.isTickingReliablyFor(3)) {
            flagAndAlert(String.format("distinct_slots=%d in %dms max=%dms ping=%dms",
                    distinctSlots, now - windowStart, maxBurstMs,
                    player.getTransactionPing()));
            slotsClickedMask = 0;
            windowStart = now;
        }
    }
}
