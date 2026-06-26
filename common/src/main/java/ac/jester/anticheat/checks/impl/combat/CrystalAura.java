package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

import java.util.ArrayDeque;

/**
 * CrystalAura — detects superhuman end crystal interaction speed.
 *
 * CrystalAura hacks automate:
 *   1. Placing an End Crystal (PLAYER_BLOCK_PLACEMENT with end crystal item)
 *   2. Attacking/detonating it (INTERACT_ENTITY ATTACK on the crystal entity)
 *
 * Detection A: Too many crystal interactions (placement or attack) per second.
 * Detection B: Crystal placed and immediately detonated within an impossibly short window.
 *
 * A human cannot manually place + click end crystals faster than ~3-4 per second.
 * CrystalAura typically operates at 10-20+ per second.
 */
@CheckData(name = "CrystalAura", description = "Interacting with end crystals at superhuman speed")
public final class CrystalAura extends Check implements PacketCheck {

    // Skilled crystal PvP legitimately reaches 12-16 interactions/sec
    private int maxInteractionsPerSecond = 16;
    private final ArrayDeque<Long> interactionTimestamps = new ArrayDeque<>();

    private long lastPlaceTime = 0L;
    private int minPlaceDetonateMs = 50;
    private int minConsecutiveFast = 3;
    private int consecutiveFastDetonates = 0;

    public CrystalAura(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxInteractionsPerSecond = config.getIntElse("CrystalAura.max-ips", 16);
        minPlaceDetonateMs = config.getIntElse("CrystalAura.min-place-detonate-ms", 50);
        minConsecutiveFast = config.getIntElse("CrystalAura.min-consecutive", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean isCrystalInteraction = false;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement place = new WrapperPlayClientPlayerBlockPlacement(event);
            var item = place.getItemStack().orElse(null);
            if (item != null && item.getType() == ItemTypes.END_CRYSTAL) {
                isCrystalInteraction = true;
                lastPlaceTime = System.currentTimeMillis();
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                // Only attacks on actual END_CRYSTAL entities count — counting
                // every attack false flagged normal PvP spam clicking as
                // "CrystalAura" the moment a player exceeded 6 cps
                var entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
                if (entity == null
                        || entity.type != com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.END_CRYSTAL) {
                    return;
                }
                isCrystalInteraction = true;

                // Check B: placed then immediately detonated — network jitter can
                // bunch one legit place+hit together, so require it consecutively
                if (lastPlaceTime != 0L) {
                    long gap = System.currentTimeMillis() - lastPlaceTime;
                    lastPlaceTime = 0L;
                    if (gap < minPlaceDetonateMs) {
                        consecutiveFastDetonates++;
                        if (consecutiveFastDetonates >= minConsecutiveFast && player.isTickingReliablyFor(3)) {
                            flagAndAlert(String.format("place->detonate gap=%dms min=%dms consecutive=%d",
                                    gap, minPlaceDetonateMs, consecutiveFastDetonates));
                            consecutiveFastDetonates = 0;
                        }
                    } else {
                        consecutiveFastDetonates = 0;
                    }
                }
            }
        }

        if (!isCrystalInteraction) return;

        // Check A: interaction rate
        long now = System.currentTimeMillis();
        interactionTimestamps.addLast(now);
        while (!interactionTimestamps.isEmpty() && now - interactionTimestamps.peekFirst() > 1000L) {
            interactionTimestamps.pollFirst();
        }

        int ips = interactionTimestamps.size();
        if (ips > maxInteractionsPerSecond && player.isTickingReliablyFor(3)) {
            flagAndAlert(String.format("ips=%d max=%d", ips, maxInteractionsPerSecond));
            interactionTimestamps.clear();
        }
    }
}
