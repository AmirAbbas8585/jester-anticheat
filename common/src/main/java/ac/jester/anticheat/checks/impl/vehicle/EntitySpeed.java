package ac.jester.anticheat.checks.impl.vehicle;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "EntitySpeed", configName = "EntitySpeed",
        description = "Ridden vehicle exceeding its maximum horizontal speed (EntitySpeed)")
public final class EntitySpeed extends Check implements PacketCheck {
    private double maxSpeed = 0.85;
    private double iceMaxSpeed = 4.0;
    private int minConsecutive = 6;
    private double ignoreAbove = 8.0;

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private double lastSpeed = 0;
    private Object lastVehicle = null;
    private int consecutive = 0;

    public EntitySpeed(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxSpeed = config.getDoubleElse("EntitySpeed.max-speed", 0.85);
        iceMaxSpeed = config.getDoubleElse("EntitySpeed.ice-max-speed", 4.0);
        minConsecutive = Math.max(1, config.getIntElse("EntitySpeed.min-consecutive", 6));
        ignoreAbove = config.getDoubleElse("EntitySpeed.ignore-above", 8.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;

        PacketEntity riding = player.compensatedEntities.self.getRiding();
        if (riding == null || riding != lastVehicle) {
            lastX = Double.NaN;
            lastSpeed = 0;
            consecutive = 0;
            lastVehicle = riding;
            if (riding == null) return;
        }

        Vector3d pos = new WrapperPlayClientVehicleMove(event).getPosition();
        if (Double.isNaN(lastX)) {
            lastX = pos.getX();
            lastZ = pos.getZ();
            return;
        }

        double dx = pos.getX() - lastX;
        double dz = pos.getZ() - lastZ;
        lastX = pos.getX();
        lastZ = pos.getZ();

        double speed = Math.sqrt(dx * dx + dz * dz);
        double previousSpeed = lastSpeed;
        lastSpeed = speed;
        if (speed > ignoreAbove) {
            consecutive = 0;
            return;
        }

        double limit = onIce(pos) ? iceMaxSpeed : maxSpeed;
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.SPEED)) limit *= 1.5;
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.DOLPHINS_GRACE)) limit *= 2.0;

        if (speed <= limit) {
            consecutive = 0;
            return;
        }

        if (speed < previousSpeed * 0.99) return;

        consecutive++;
        if (consecutive >= minConsecutive
                && player.isTickingReliablyFor(5) && player.getTransactionPing() < 500) {
            flagAndAlert(String.format("vehicle=%s speed=%.2f max=%.2f consecutive=%d ping=%dms",
                    riding.type == null ? "?" : riding.type.getName().getKey(),
                    speed, limit, consecutive, player.getTransactionPing()));
            consecutive = 0;
        }
    }

    private boolean onIce(Vector3d pos) {
        for (double dy : new double[]{-0.1, -0.6, -1.2}) {
            StateType t = player.compensatedWorld.getBlock(pos.getX(), pos.getY() + dy, pos.getZ()).getType();
            if (t == StateTypes.BLUE_ICE || t == StateTypes.PACKED_ICE || t == StateTypes.ICE
                    || t == StateTypes.FROSTED_ICE) {
                return true;
            }
        }
        return false;
    }
}
