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

/**
 * BoatFly — vehicles gaining altitude in mid-air.
 *
 * In vanilla, a boat (or any ridden vehicle) cannot ascend: out of water it
 * only falls. The only legitimate lift sources are water, bubble columns, and
 * the single splash-out hop — all of which involve water at or directly below
 * the vehicle. BoatFly hacks send VEHICLE_MOVE packets climbing through air.
 *
 * Detection: while driving, count consecutive ascending VEHICLE_MOVE packets
 * with no water/bubble column at, below, or one block under the vehicle.
 * Flag after minRiseTicks consecutive ascents totalling more than minRise
 * blocks — a one-tick bounce (slime, collision jitter) never triggers it.
 */
@CheckData(name = "BoatFly", configName = "BoatFly",
        description = "Vehicle ascending in mid-air (impossible without water)")
public final class BoatFly extends Check implements PacketCheck {

    private int minRiseTicks = 15;
    private double minRise = 3.0;

    private double lastY = Double.NaN;
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
        // Boats only: horses legitimately rise 12+ ticks on a max-strength jump
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

        // Any water/bubble involvement = legitimate lift
        if (nearWater(pos.getX(), pos.getY(), pos.getZ())) {
            reset();
            lastY = pos.getY();
            return;
        }

        // Solid ground under the boat = sliding up an icy slope / pushed —
        // BoatFly means ascending through AIR specifically
        if (!isAir(pos.getX(), pos.getY() - 0.6, pos.getZ())
                || !isAir(pos.getX(), pos.getY() - 1.2, pos.getZ())) {
            reset();
            return;
        }

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
    }
}
