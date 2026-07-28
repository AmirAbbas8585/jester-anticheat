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

/**
 * EntitySpeed — a ridden vehicle moving faster horizontally than it can.
 *
 * Covers the "EntitySpeed"/"EntityControl" cheats that make a boat or mount move
 * far faster than vanilla allows. The existing Vehicle checks only validate
 * INPUTS (impossible values, jumping on something that can't jump, paddle state),
 * and BoatClip only bounds a single teleport-sized jump — none of them bound
 * sustained travel speed, which is what these cheats actually change.
 *
 * The hard part is that vanilla boat speed varies enormously with the surface:
 * on water a boat tops out around 0.4 blocks/tick, but on BLUE ICE it legitimately
 * reaches many times that — ice-boat highways are a normal way to travel. A single
 * flat limit would either miss the cheat on water or ban ice travel. So the limit
 * is chosen from the block under the vehicle: ice gets the high limit, everything
 * else the normal one.
 *
 * False-positive protections (this check can kick, so these matter):
 *   - Ice/packed ice/blue ice under the vehicle switches to iceMaxSpeed (default
 *     4.0 blocks/tick ≈ 80 blocks/s), far above any real ice-boat run.
 *   - Speed and Dolphin's Grace on the rider are allowed for by a large margin.
 *   - Vertical motion is ignored entirely; only horizontal distance is measured.
 *   - A single over-limit packet never flags: minConsecutive (default 6) ticks in
 *     a row are required, and any normal-speed tick resets the streak. Packet
 *     bunching under lag produces one big delta, not six in a row.
 *   - Only while ticking reliably and under 500ms ping.
 *   - Teleport-sized deltas are ignored (that's BoatClip's job, and a plugin
 *     teleport would otherwise look like infinite speed).
 */
@CheckData(name = "EntitySpeed", configName = "EntitySpeed",
        description = "Ridden vehicle exceeding its maximum horizontal speed (EntitySpeed)")
public final class EntitySpeed extends Check implements PacketCheck {

    private double maxSpeed = 0.85;
    private double iceMaxSpeed = 4.0;
    private int minConsecutive = 6;
    /** Above this a delta is a teleport/lag jump, not travel — ignored here. */
    private double ignoreAbove = 8.0;

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
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
        if (riding == null) {
            lastX = Double.NaN;
            consecutive = 0;
            return;
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
        if (speed > ignoreAbove) { // teleport / chunk-load jump — not our business
            consecutive = 0;
            return;
        }

        double limit = onIce(pos) ? iceMaxSpeed : maxSpeed;
        // Generous headroom for legitimate rider effects.
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.SPEED)) limit *= 1.5;
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.DOLPHINS_GRACE)) limit *= 2.0;

        if (speed <= limit) {
            consecutive = 0;
            return;
        }

        consecutive++;
        if (consecutive >= minConsecutive
                && player.isTickingReliablyFor(5) && player.getTransactionPing() < 500) {
            flagAndAlert(String.format("vehicle=%s speed=%.2f max=%.2f consecutive=%d ping=%dms",
                    riding.type == null ? "?" : riding.type.getName().getKey(),
                    speed, limit, consecutive, player.getTransactionPing()));
            consecutive = 0;
        }
    }

    /** Ice under the vehicle legitimately allows far higher speeds. */
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
