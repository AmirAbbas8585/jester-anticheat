package ac.jester.anticheat.checks.impl.vehicle;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "BoatFly", configName = "BoatFly",
        description = "Vehicle ascending in mid-air (impossible without water)")
public final class BoatFly extends Check implements PacketCheck {
    private int minRiseTicks = 15;
    private double minRise = 3.0;

    private double lastY = Double.NaN;
    private double lastDy = Double.MAX_VALUE;
    private int risingTicks = 0;
    private double totalRise = 0;

    public BoatFly(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minRiseTicks = config.getIntElse("BoatFly.min-rise-ticks", 15);
        minRise = config.getDoubleElse("BoatFly.min-rise", 3.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;

        PacketEntity riding = player.compensatedEntities.self.getRiding();
        if (riding == null || !riding.isBoat) {
            reset();
            lastY = Double.NaN;
            return;
        }

        WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
        Vector3d pos = move.getPosition();

        if (Double.isNaN(lastY)) {
            lastY = pos.getY();
            return;
        }

        double dy = pos.getY() - lastY;
        lastY = pos.getY();

        if (dy <= 0.001) {
            reset();
            lastY = pos.getY();
            return;
        }

        if (nearWater(pos.getX(), pos.getY(), pos.getZ())) {
            reset();
            lastY = pos.getY();
            return;
        }

        if (!isAir(pos.getX(), pos.getY() - 0.6, pos.getZ())
                || !isAir(pos.getX(), pos.getY() - 1.2, pos.getZ())) {
            reset();
            return;
        }

        if (dy < lastDy - 0.001) {
            lastDy = dy;
            return;
        }
        lastDy = dy;

        risingTicks++;
        totalRise += dy;

        if (risingTicks >= minRiseTicks && totalRise >= minRise
                && player.isTickingReliablyFor(5) && player.getTransactionPing() < 500) {
            flagAndAlert(String.format("risingTicks=%d rise=%.2f ping=%dms",
                    risingTicks, totalRise, player.getTransactionPing()));
            reset();
        }
    }

    private boolean nearWater(double x, double y, double z) {
        for (int dy = -1; dy <= 1; dy++) {
            WrappedBlockState state = player.compensatedWorld.getBlock(x, y + dy, z);
            if (Materials.isWater(player.getClientVersion(), state)) return true;
            if (state.getType() == StateTypes.BUBBLE_COLUMN) return true;
        }
        return false;
    }

    private boolean isAir(double x, double y, double z) {
        var type = player.compensatedWorld.getBlock(x, y, z).getType();
        return type == StateTypes.AIR || type == StateTypes.CAVE_AIR || type == StateTypes.VOID_AIR;
    }

    private void reset() {
        risingTicks = 0;
        totalRise = 0;
        lastDy = Double.MAX_VALUE;
    }
}
