package ac.jester.anticheat.checks.impl.sprint;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "SprintA", description = "Sprinting with too low hunger", setback = 0)
public class SprintA extends Check implements PacketCheck {
    public SprintA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate || player.packetStateData.lastPacketWasTeleport) return;

        if (player.canFly || EntityTypes.isTypeInstanceOf(player.getVehicleType(), EntityTypes.CAMEL)) return;

        if (player.food <= 6.0F) {
            if (player.isSprinting) {
                if (flagAndAlert("hunger=" + player.food)) {
                    if (shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    setbackIfAboveSetbackVL();
                }
            } else {
                reward();
            }
        }
    }
}
