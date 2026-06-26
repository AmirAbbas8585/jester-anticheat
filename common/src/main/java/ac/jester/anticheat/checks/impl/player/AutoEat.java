package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;

/**
 * AutoEat — detects automatically eating the instant hunger drops, faster
 * than a human glancing at the hunger bar and clicking could react.
 *
 * Unlike AutoPot (an emergency reaction to damage), eating isn't urgent, so a
 * real player's reaction time varies a lot and is often slow. The tell isn't
 * a single fast reaction — it's reacting *consistently* fast on every single
 * hunger drop, with none of the natural variance/delay of someone who is busy
 * mining, fighting, or building and eats whenever convenient.
 *
 * Detection: when UPDATE_HEALTH shows the food level decrease, record the
 * time. If USE_ITEM on a food item (anything with a FOOD component — covers
 * custom ItemsAdder food too, not just vanilla) follows within minReactMs,
 * count it as fast. Only flag after several consecutive fast reactions.
 */
@CheckData(name = "AutoEat", description = "Eating food within milliseconds of every hunger drop")
public final class AutoEat extends Check implements PacketCheck {

    private int trackedFood = 20;
    private long lastHungerDropTime = 0L;
    private int minReactMs = 100;
    // Eating isn't an emergency reaction like AutoPot's combat trigger, so one
    // fast click is plausible coincidence (already had food in hand, cursor
    // ready). Only sustained, every-single-time speed is the bot.
    private int minConsecutive = 5;
    private int consecutiveFast = 0;

    public AutoEat(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minReactMs = config.getIntElse("AutoEat.min-react-ms", 100);
        minConsecutive = config.getIntElse("AutoEat.min-consecutive", 5);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_HEALTH) return;

        WrapperPlayServerUpdateHealth pkt = new WrapperPlayServerUpdateHealth(event);
        int newFood = pkt.getFood();

        if (newFood < trackedFood) {
            lastHungerDropTime = System.currentTimeMillis();
        }
        trackedFood = newFood;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (lastHungerDropTime == 0L) return;

        InteractionHand hand = new WrapperPlayClientUseItem(event).getHand();
        ItemStack used = hand == InteractionHand.MAIN_HAND
                ? player.inventory.getHeldItem()
                : player.inventory.getOffHand();
        if (!isFood(used)) return;

        long elapsed = System.currentTimeMillis() - lastHungerDropTime;
        lastHungerDropTime = 0L;

        if (!player.isTickingReliablyFor(3)) return;
        if (player.getTransactionPing() > 500) return;

        if (elapsed < minReactMs) {
            consecutiveFast++;
            if (consecutiveFast >= minConsecutive) {
                flagAndAlert(String.format("react=%dms min=%dms consecutive=%d ping=%dms",
                        elapsed, minReactMs, consecutiveFast, player.getTransactionPing()));
                consecutiveFast = 0;
            }
        } else {
            consecutiveFast = 0;
        }
    }

    private static boolean isFood(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return item.getComponentOr(ComponentTypes.FOOD, null) != null;
    }
}
