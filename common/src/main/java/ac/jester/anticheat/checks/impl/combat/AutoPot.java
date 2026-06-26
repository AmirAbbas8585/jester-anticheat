package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;

/**
 * AutoPot — detects using a potion within milliseconds of taking damage.
 *
 * AutoPot (also called AutoSoup or AutoHeal in some hack clients) automatically
 * throws/drinks a healing potion the moment the client receives damage. The
 * server sends UPDATE_HEALTH with a lower value; the bot immediately responds
 * with a USE_ITEM (drink) or PLAYER_BLOCK_PLACEMENT (throw) for the potion.
 *
 * A human requires at minimum ~80ms to react to a visual damage indicator,
 * then another ~100ms to click. AutoPot typically reacts in < 20ms.
 *
 * Detection: when UPDATE_HEALTH shows a health decrease (> 0.5HP), record the
 * time. If USE_ITEM with a potion follows within minReactMs, flag it.
 *
 * Only POTION, SPLASH_POTION, and LINGERING_POTION are checked — this avoids
 * false positives from food or other consumables.
 *
 * Note: this check requires the player to be actively taking damage first.
 * Players who proactively pre-pot before combat are not flagged.
 */
@CheckData(name = "AutoPot", description = "Using a healing potion within milliseconds of taking damage")
public final class AutoPot extends Check implements PacketCheck {

    private float trackedHealth = 20.0f;
    private long lastDamageTime = 0L;
    private int minReactMs = 80;
    // In a fight a player splashing pots frequently WILL land an occasional
    // click inside the reaction window by pure coincidence — only repetition
    // distinguishes the bot
    private int minConsecutive = 3;
    private int consecutiveFast = 0;

    public AutoPot(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minReactMs = config.getIntElse("AutoPot.min-react-ms", 80);
        minConsecutive = config.getIntElse("AutoPot.min-consecutive", 3);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_HEALTH) return;

        WrapperPlayServerUpdateHealth pkt = new WrapperPlayServerUpdateHealth(event);
        float newHealth = pkt.getHealth();

        // Record damage: health decreased by at least 0.5 and player is still alive
        if (newHealth > 0 && newHealth < trackedHealth - 0.5f) {
            lastDamageTime = System.currentTimeMillis();
        }
        trackedHealth = newHealth;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (lastDamageTime == 0L) return;

        // Splash potions are thrown (PLAYER_BLOCK_PLACEMENT), drinkable potions use USE_ITEM
        InteractionHand hand;
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            hand = new WrapperPlayClientUseItem(event).getHand();
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            hand = new WrapperPlayClientPlayerBlockPlacement(event).getHand();
        } else {
            return;
        }

        // Only inspect the item in the hand that was actually used —
        // eating a gapple with a potion sitting in the offhand must not flag
        ItemStack used = hand == InteractionHand.MAIN_HAND
                ? player.inventory.getHeldItem()
                : player.inventory.getOffHand();
        if (!isPot(used)) return;

        long elapsed = System.currentTimeMillis() - lastDamageTime;
        lastDamageTime = 0L;

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

    private static boolean isPot(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        ItemType type = item.getType();
        return type == ItemTypes.POTION
                || type == ItemTypes.SPLASH_POTION
                || type == ItemTypes.LINGERING_POTION;
    }
}
