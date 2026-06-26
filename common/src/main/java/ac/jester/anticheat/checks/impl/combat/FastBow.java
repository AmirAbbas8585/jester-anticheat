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
 * FastBow — detects releasing a bow before the minimum charge time.
 *
 * In vanilla 1.9+, a bow must be held (right-click) for at least 3 ticks (~150ms)
 * before it fires. Releasing within 1 tick (50ms) or less is physically impossible
 * and indicates a FastBow / InstaShoot hack.
 *
 * Detection: measure the elapsed time between USE_ITEM (start draw) and
 * RELEASE_USE_ITEM (fire). If below the threshold, flag.
 */
@CheckData(name = "FastBow", description = "Releasing bow before minimum charge time")
public final class FastBow extends Check implements PacketCheck {

    private long bowDrawStartMs = 0L;
    private int minChargeMs = 45;

    public FastBow(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minChargeMs = config.getIntElse("FastBow.min-charge-ms", 45);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) return;

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            // Only track bow draws
            if (isBowInHand()) {
                bowDrawStartMs = System.currentTimeMillis();
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() != DiggingAction.RELEASE_USE_ITEM) return;
            if (bowDrawStartMs == 0L) return;

            long chargeTime = System.currentTimeMillis() - bowDrawStartMs;
            bowDrawStartMs = 0L;

            if (!isBowInHand()) return;
            if (!player.isTickingReliablyFor(3)) return;
            if (player.getTransactionPing() > 800) return;

            if (chargeTime < minChargeMs) {
                flagAndAlert(String.format("charge=%dms min=%dms ping=%dms",
                        chargeTime, minChargeMs, player.getTransactionPing()));
            }
        }

        // Slot change cancels the bow draw
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            bowDrawStartMs = 0L;
        }
    }

    private boolean isBowInHand() {
        var heldItem = player.inventory.getHeldItem();
        return heldItem != null && heldItem.is(ItemTypes.BOW);
    }
}
