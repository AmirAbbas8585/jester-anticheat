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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * AutoBlock — detects attacking while blocking with a shield.
 *
 * In vanilla 1.9+, a player cannot left-click attack while actively using an
 * item (holding right-click to block with a shield). AutoBlock hacks attack
 * without releasing the shield.
 *
 * State source: grim's latency-compensated isSlowedByUsingItem() — our own
 * USE_ITEM/RELEASE bookkeeping got stuck whenever an item use ended without a
 * RELEASE packet (finished eating, failed food use on full hunger), which
 * false flagged AND cancelled the player's next legitimate attack.
 *
 * Shield requirement keeps this check distinct from Multitask (which covers
 * all other items); consecutive requirement absorbs state-edge races.
 */
@CheckData(name = "AutoBlock", description = "Attacking while blocking with a shield")
public final class AutoBlock extends Check implements PacketCheck {

    private int maxPingMs = 500;
    private int minConsecutive = 2;
    private int consecutive = 0;

    public AutoBlock(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPingMs = config.getIntElse("AutoBlock.max-ping", 500);
        minConsecutive = config.getIntElse("AutoBlock.min-consecutive", 2);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Only meaningful on 1.9+ where shield blocking exists
        if (!PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) return;

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (!player.packetStateData.isSlowedByUsingItem() || !holdingShield()) {
            consecutive = 0;
            return;
        }

        if (player.getTransactionPing() > maxPingMs) return;
        if (!player.isTickingReliablyFor(3)) return;

        consecutive++;
        if (consecutive >= minConsecutive) {
            if (flagAndAlert("consecutive=" + consecutive + " ping=" + player.getTransactionPing())
                    && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            consecutive = 0;
        }
    }

    private boolean holdingShield() {
        var main = player.inventory.getHeldItem();
        var off = player.inventory.getOffHand();
        return (main != null && main.is(ItemTypes.SHIELD)) || (off != null && off.is(ItemTypes.SHIELD));
    }
}
