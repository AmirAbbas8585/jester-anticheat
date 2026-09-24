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

        if (click.getWindowId() != 0) return;

        var slotsOpt = click.getSlots();
        if (slotsOpt.isPresent()) {
            var slots = slotsOpt.get();
            var offhandItem = slots.get(SLOT_OFFHAND_WINDOW);
            if (offhandItem != null && offhandItem.getType() == ItemTypes.TOTEM_OF_UNDYING) {
                totemGoingToOffhand = true;
            }
        }

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

        long reaction = elapsed - player.getTransactionPing();

        if (reaction < 0) return;

        if (reaction < minReactMs) {
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
