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

@CheckData(name = "PacketOrderL", description = "Sent drop, inventory open, or offhand swap packets in an invalid order", experimental = true)
public class PacketOrderL extends Check implements PostPredictionCheck {
    public PacketOrderL(final GrimPlayer player) {
        super(player);
    }

    private final ArrayDeque<String> flags = new ArrayDeque<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_STATUS) {
            if (new WrapperPlayClientClientStatus(event).getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
                if (player.packetOrderProcessor.isDropping()) {
                    if (!player.canSkipTicks()) {
                        if (flagAndAlert("inventory") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else {
                        flags.add("inventory");
                    }
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            if (new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                if (player.packetOrderProcessor.isDropping()) {
                    if (!player.canSkipTicks()) {
                        if (flagAndAlert("swap") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else {
                        flags.add("swap");
                    }
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
