package ac.jester.anticheat.checks.impl.packetorder;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;

@CheckData(name = "PacketOrderG", description = "Managed hotbar or inventory while performing another conflicting action", experimental = true)
public class PacketOrderG extends Check implements PostPredictionCheck {
    public PacketOrderG(GrimPlayer player) {
        super(player);
    }

    private final ArrayDeque<String> flags = new ArrayDeque<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING || (event.getPacketType() == PacketType.Play.Client.CLIENT_STATUS
                && new WrapperPlayClientClientStatus(event).getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT)) {
            DiggingAction action = null;
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                action = new WrapperPlayClientPlayerDigging(event).getAction();
                if (action != DiggingAction.SWAP_ITEM_WITH_OFFHAND
                        && action != DiggingAction.DROP_ITEM
                        && action != DiggingAction.DROP_ITEM_STACK
                ) return;
            }

            if (player.packetOrderProcessor.isAttackingOrStabbing()
                    || player.packetOrderProcessor.isReleasing()
                    || player.packetOrderProcessor.isRightClicking()
                    || player.packetOrderProcessor.isPicking()
                    || player.packetOrderProcessor.isDigging()
            ) {
                String verbose = "action=" + (action == null ? "openInventory" : action == DiggingAction.SWAP_ITEM_WITH_OFFHAND ? "swap" : "drop")
                        + ", attacking=" + player.packetOrderProcessor.isAttackingOrStabbing()
                        + ", releasing=" + player.packetOrderProcessor.isReleasing()
                        + ", rightClicking=" + player.packetOrderProcessor.isRightClicking()
                        + ", picking=" + player.packetOrderProcessor.isPicking()
                        + ", digging=" + player.packetOrderProcessor.isDigging();
                if (!player.canSkipTicks()) {
                    if (flagAndAlert(verbose) && shouldModifyPackets() && canCancel(action)) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    flags.add(verbose);
                }
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (String verbose : flags) {
                flagAndAlert(verbose);
            }
        }

        flags.clear();
    }
}
