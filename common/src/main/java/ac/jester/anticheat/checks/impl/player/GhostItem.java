package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

/**
 * GhostItem — using an item the server doesn't see in the player's hand.
 *
 * When the client sends USE_ITEM (right-click use), the server-side inventory
 * view must have a non-empty item in the used hand — you cannot "use" an empty
 * hand in vanilla. A cheat that desyncs its inventory (a "ghost"/fake item)
 * fires USE_ITEM while the server believes the hand is empty.
 *
 * Legit slot-switch / pickup desyncs can leave the server view empty for a tick,
 * so a streak and a ticking/ping guard are required. Experimental + alert-only
 * by default — calibrate before enabling kicks.
 */
@CheckData(name = "GhostItem", experimental = true,
        description = "Using an item the server doesn't see in the player's hand (ghost/fake item)")
public final class GhostItem extends Check implements PacketCheck {

    private static final boolean OFFHAND =
            PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9);

    private int minConsecutive = 2;
    private int maxPingMs = 600;
    private int consecutive = 0;

    public GhostItem(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = Math.max(1, config.getIntElse("GhostItem.min-consecutive", 2));
        maxPingMs = config.getIntElse("GhostItem.max-ping", 600);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;

        InteractionHand hand = OFFHAND ? new WrapperPlayClientUseItem(event).getHand() : InteractionHand.MAIN_HAND;
        ItemStack item = player.inventory.getItemInHand(hand);

        // Server sees a real item in that hand — nothing ghostly.
        if (item != null && !item.isEmpty()) {
            consecutive = 0;
            return;
        }

        // Transient inventory desync (slot switch, pickup) can briefly look empty.
        if (!player.isTickingReliablyFor(3) || player.getTransactionPing() > maxPingMs) return;

        if (++consecutive >= minConsecutive) {
            flagAndAlert("hand=" + hand + " serverItem=empty ping=" + player.getTransactionPing() + "ms");
            consecutive = 0;
        }
    }
}
