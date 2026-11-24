package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.PistonData;
import ac.jester.anticheat.utils.data.ShulkerData;
import ac.jester.anticheat.utils.data.movable.PistonBlockEvent;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;

public class PacketBlockAction extends PacketListenerAbstract {
    public PacketBlockAction() {
        super(PacketListenerPriority.HIGH);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.BLOCK_ACTION) return;

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        WrapperPlayServerBlockAction blockAction = new WrapperPlayServerBlockAction(event);
        Vector3i blockPos = blockAction.getBlockPosition();
        int actionId = blockAction.getActionId();
        int actionData = blockAction.getActionData();
        int transaction = player.lastTransactionSent.get();

        player.latencyUtils.addRealTimeTask(transaction, () -> {
            WrappedBlockState existing = player.compensatedWorld.getBlock(blockPos);
            PistonData piston = PistonBlockEvent.handle(
                    player, blockPos.getX(), blockPos.getY(), blockPos.getZ(), actionId, transaction);
            if (piston != null) {
                player.compensatedWorld.activePistons.add(piston);
            } else if (Materials.isShulker(existing.getType())) {
                ShulkerData data = new ShulkerData(blockPos, player.lastTransactionSent.get(), actionData < 1);
                player.compensatedWorld.openShulkerBoxes.remove(data);
                player.compensatedWorld.openShulkerBoxes.add(data);
            }
        });
    }
}
