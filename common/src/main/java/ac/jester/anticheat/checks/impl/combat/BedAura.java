package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

/**
 * BedAura — detects rapid bed-interaction exploitation.
 *
 * In the Nether and End dimensions, right-clicking a bed causes an explosion.
 * BedAura (also called BedBomb/BedPop in various hacked clients) automates
 * placing and right-clicking beds at superhuman speed to continuously spawn
 * explosions at a target's position — a common griefing and PvP technique.
 *
 * Detection: Track PLAYER_BLOCK_PLACEMENT packets while the player holds a
 * bed-type item. BedAura typically fires 10-20 interactions per second.
 * The default threshold (8/s) is above human jitter-clicking range (~5-7/s) —
 * note that right-clicking ANY block (doors, buttons) while holding a bed
 * also sends placement packets, so the threshold must tolerate click spam.
 *
 * We use a rolling 1-second window. The server-side dimension check (Nether/End)
 * is intentionally omitted so operators are alerted regardless of dimension —
 * bed building in the Overworld at that speed is equally suspicious.
 *
 * Bed items: white_bed, orange_bed, magenta_bed, light_blue_bed, yellow_bed,
 * lime_bed, pink_bed, gray_bed, light_gray_bed, cyan_bed, purple_bed, blue_bed,
 * brown_bed, green_bed, red_bed, black_bed.
 * All have item type names ending in "_bed".
 */
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

        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty()) return;

        // All bed item types end with "_bed" in their registry name
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
