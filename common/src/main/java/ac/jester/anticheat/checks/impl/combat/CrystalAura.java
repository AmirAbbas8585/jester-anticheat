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

@CheckData(name = "CrystalAura", description = "Interacting with end crystals at superhuman speed")
public final class CrystalAura extends Check implements PacketCheck {
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
            var item = place.getHand() == com.github.retrooper.packetevents.protocol.player.InteractionHand.OFF_HAND
                    ? player.inventory.getOffHand()
                    : player.inventory.getHeldItem();
            if (item != null && item.getType() == ItemTypes.END_CRYSTAL) {
                lastPlaceTime = System.currentTimeMillis();
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                var entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
                if (entity == null
                        || entity.type != com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.END_CRYSTAL) {
                    return;
                }
                isCrystalInteraction = true;

                if (lastPlaceTime != 0L) {
                    long gap = System.currentTimeMillis() - lastPlaceTime;
                    lastPlaceTime = 0L;
                    long physical = player.getTransactionPing() / 2;
                    if (gap < minPlaceDetonateMs && gap < physical) {
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
