package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "BedAura", description = "Rapidly interacting with beds to trigger explosions")
public final class BedAura extends Check implements PacketCheck {
    private int maxPerSecond = 8;

    private int bedInteractCount = 0;
    private long windowStart = 0L;

    public BedAura(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPerSecond = config.getIntElse("BedAura.max-per-second", 8);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement(event).getHand()
                != com.github.retrooper.packetevents.protocol.player.InteractionHand.MAIN_HAND) return;

        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty()) return;

        String typeName = held.getType().getName().toString();
        if (!typeName.endsWith("_bed")) return;

        if (!player.isTickingReliablyFor(3)) return;

        long now = System.currentTimeMillis();
        if (now - windowStart > 1000L) {
            bedInteractCount = 0;
            windowStart = now;
        }

        bedInteractCount++;

        if (bedInteractCount > maxPerSecond) {
            flagAndAlert(String.format("bed_cps=%d max=%d ping=%dms",
                    bedInteractCount, maxPerSecond, player.getTransactionPing()));
            bedInteractCount = 0;
            windowStart = now;
        }
    }
}
