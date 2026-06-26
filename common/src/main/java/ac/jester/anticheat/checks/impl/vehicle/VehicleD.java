package ac.jester.anticheat.checks.impl.vehicle;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "VehicleD", experimental = true, description = "Jumped in a vehicle that cannot jump")
public class VehicleD extends Check implements PacketCheck {
    public VehicleD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION && new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.START_JUMPING_WITH_HORSE) {
            final EntityType vehicle = player.getVehicleType();

            if (!EntityTypes.isTypeInstanceOf(vehicle, EntityTypes.ABSTRACT_HORSE) && !EntityTypes.isTypeInstanceOf(vehicle, EntityTypes.ABSTRACT_NAUTILUS)) {
                if (flagAndAlert("vehicle=" + (vehicle == null ? "null" : vehicle.getName().getKey().toLowerCase())) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
