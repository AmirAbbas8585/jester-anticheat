package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;

/**
 * AutoTotem — detects automatically re-equipping a totem after it pops.
 *
 * When a Totem of Undying saves a player, the server sends ENTITY_STATUS 35.
 * The offhand slot is then empty. A vanilla player must manually open their
 * inventory and drag a new totem to the offhand — this takes at least 400ms
 * even for skilled players (inventory open → locate totem → drag → close).
 *
 * AutoTotem sends a CLICK_WINDOW packet placing the totem in the offhand
 * slot within milliseconds of the pop, without even opening the inventory.
 *
 * Detection: measure elapsed time between ENTITY_STATUS 35 (totem pop) and
 * the CLICK_WINDOW that places a TOTEM_OF_UNDYING in the offhand slot (slot 45
 * in player inventory window, or F-key swap with button 40).
 */
@CheckData(name = "AutoTotem", description = "Re-equipping totem faster than humanly possible after it pops")
public final class AutoTotem extends Check implements PacketCheck {

    private static final int ENTITY_STATUS_TOTEM = 35;
    private static final int SLOT_OFFHAND_WINDOW = 45;
    private static final int BUTTON_SWAP_OFFHAND = 40;

    private long lastTotemPopTime = 0L;
    private int minReactMs = 150;
    private int minConsecutive = 2;
    private int consecutiveFast = 0;

    public AutoTotem(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minReactMs = config.getIntElse("AutoTotem.min-react-ms", 150);
        minConsecutive = config.getIntElse("AutoTotem.min-consecutive", 2);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) return;

        WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
        if (status.getEntityId() == player.entityID && status.getStatus() == ENTITY_STATUS_TOTEM) {
            lastTotemPopTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;
        if (lastTotemPopTime == 0L) return;

        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);

        boolean totemGoingToOffhand = false;

        // Pattern 1: placing totem into offhand slot by dragging in inventory
        var slotsOpt = click.getSlots();
        if (slotsOpt.isPresent()) {
            var slots = slotsOpt.get();
            var offhandItem = slots.get(SLOT_OFFHAND_WINDOW);
            if (offhandItem != null && offhandItem.getType() == ItemTypes.TOTEM_OF_UNDYING) {
                totemGoingToOffhand = true;
            }
        }

        // Pattern 2: F-key swap to move held totem to offhand (button = 40)
        if (!totemGoingToOffhand
                && click.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP
                && click.getButton() == BUTTON_SWAP_OFFHAND) {
            var held = player.inventory.getHeldItem();
            if (held != null && held.getType() == ItemTypes.TOTEM_OF_UNDYING) {
                totemGoingToOffhand = true;
            }
        }

        if (!totemGoingToOffhand) return;

        long elapsed = System.currentTimeMillis() - lastTotemPopTime;
        lastTotemPopTime = 0L;

        if (!player.isTickingReliablyFor(3)) return;
        if (player.getTransactionPing() > 600) return;

        // The pop packet travels to the client and the click back — subtract
        // the round trip so only the player's actual reaction is judged
        long reaction = elapsed - player.getTransactionPing();

        // Negative reaction = the restock click came back faster than a full
        // round trip. That's a measurement artifact (ping spike, or a pre-emptive
        // click already in flight before the pop), NOT superhuman reaction —
        // ignore it so pre-clicking totems doesn't false flag.
        if (reaction < 0) return;

        if (reaction < minReactMs) {
            // One fast restock can be luck (inventory already open, cursor on
            // the totem mid-fight); doing it on EVERY pop is the mod
            consecutiveFast++;
            if (consecutiveFast >= minConsecutive) {
                flagAndAlert(String.format("react=%dms min=%dms consecutive=%d ping=%dms",
                        reaction, minReactMs, consecutiveFast, player.getTransactionPing()));
                consecutiveFast = 0;
            }
        } else {
            consecutiveFast = 0;
        }
    }
}
