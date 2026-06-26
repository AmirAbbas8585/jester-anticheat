package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

/**
 * AnchorAura — detects rapid Respawn Anchor interaction to trigger explosions.
 *
 * Respawn Anchors explode when right-clicked in dimensions other than the Nether
 * (Overworld and End). AnchorAura automates clicking on placed Respawn Anchors at
 * superhuman speed to produce a rapid series of explosions — similar to BedAura.
 *
 * Detection: track PLAYER_BLOCK_PLACEMENT packets while holding a RESPAWN_ANCHOR
 * item. If the rate exceeds maxPerSecond interactions per second, flag it.
 *
 * The dimension check is intentionally omitted — high-rate anchor interaction is
 * suspicious regardless of dimension (placing > 4 per second is not normal building).
 */
@CheckData(name = "AnchorAura", description = "Rapidly interacting with respawn anchors to trigger explosions")
public final class AnchorAura extends Check implements PacketCheck {

    private int maxPerSecond = 8;

    private int anchorCount = 0;
    private long windowStart = 0L;

    public AnchorAura(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPerSecond = config.getIntElse("AnchorAura.max-per-second", 8);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;

        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty()) return;
        if (held.getType() != ItemTypes.RESPAWN_ANCHOR) return;

        if (!player.isTickingReliablyFor(3)) return;

        long now = System.currentTimeMillis();
        if (now - windowStart > 1000L) {
            anchorCount = 0;
            windowStart = now;
        }

        anchorCount++;
        if (anchorCount > maxPerSecond) {
            flagAndAlert(String.format("anchor_cps=%d max=%d ping=%dms",
                    anchorCount, maxPerSecond, player.getTransactionPing()));
            anchorCount = 0;
            windowStart = now;
        }
    }
}
