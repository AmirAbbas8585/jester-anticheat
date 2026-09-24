package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;

@CheckData(name = "AutoArmor", description = "Automatically equipping armor at superhuman speed")
public final class AutoArmor extends Check implements PacketCheck {
    private static final int SLOT_HELMET = 5;
    private static final int SLOT_BOOTS = 8;

    private int maxBurstMs = 250;
    private int burstThreshold = 2;

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

        if (click.getWindowId() != 0) return;

        int slot = click.getSlot();
        if (slot < SLOT_HELMET || slot > SLOT_BOOTS) return;

        var changed = click.getSlots();
        if (changed.isEmpty()) return;
        var after = changed.get().get(slot);
        if (after == null || after.isEmpty()) return;

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
