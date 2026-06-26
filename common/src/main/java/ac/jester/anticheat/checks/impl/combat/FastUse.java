package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * FastUse — detects using consumable items faster than vanilla timing allows.
 *
 * Vanilla minimum use times (in ticks):
 *  - Food: 32 ticks (1600ms)
 *  - Potion: 32 ticks (1600ms)
 *  - Milk bucket: 32 ticks
 *  - Golden apple / Enchanted golden apple: 32 ticks
 *
 * FastUse hacks release USE_ITEM far earlier than the vanilla minimum,
 * allowing instant eating/drinking for an unfair regeneration advantage.
 */
@CheckData(name = "FastUse", description = "Using consumable items faster than vanilla minimum timing")
public final class FastUse extends Check implements PacketCheck {

    private long useStartMs = 0L;
    private boolean trackingUse = false;
    private int minUseMs = 1500;

    public FastUse(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minUseMs = config.getIntElse("FastUse.min-use-ms", 1500);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) return;

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            if (isConsumableInHand()) {
                trackingUse = true;
                useStartMs = System.currentTimeMillis();
            } else {
                trackingUse = false;
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            trackingUse = false;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() != DiggingAction.RELEASE_USE_ITEM) return;
            if (!trackingUse) return;

            trackingUse = false;
            long elapsed = System.currentTimeMillis() - useStartMs;

            if (!player.isTickingReliablyFor(3)) return;
            if (player.getTransactionPing() > 800) return;

            if (elapsed < minUseMs) {
                flagAndAlert(String.format("elapsed=%dms min=%dms ping=%dms",
                        elapsed, minUseMs, player.getTransactionPing()));
            }
        }
    }

    private boolean isConsumableInHand() {
        var item = player.inventory.getHeldItem();
        if (item == null) return false;
        var type = item.getType();
        // Check for potions, milk, honey, and all edible food items
        return type == ItemTypes.POTION
                || type == ItemTypes.MILK_BUCKET
                || type == ItemTypes.HONEY_BOTTLE
                || type == ItemTypes.OMINOUS_BOTTLE
                || type.hasAttribute(ItemTypes.ItemAttribute.EDIBLE);
    }
}
