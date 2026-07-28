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
 * EntityFly — a ridden MOB climbing through the air.
 *
 * BoatFly already covers boats and deliberately skips everything else, because a
 * horse legitimately rises for a dozen-plus ticks on a jump. That left the whole
 * "EntityFly"/"EntityControl" family of cheats — flying around on a horse, pig,
 * strider or any other mount — completely uncovered. This closes that gap.
 *
 * The distinction the check leans on is BALLISTICS, not height. A real mount jump
 * is a parabola: the client is fighting gravity, so each tick rises strictly less
 * than the one before, and the whole arc is over quickly. A fly hack has to keep
 * feeding upward motion, so its ascent does not decay. We therefore only count
 * ticks that DON'T look like the tail of a jump, and require a long run of them.
 *
 * False-positive protections (this check can kick, so these matter):
 *   - Happy ghasts are flying mounts by design and are exempt outright.
 *   - Any water/bubble column at, above or below the mount resets it.
 *   - Solid ground under the mount resets it (riding up stairs/slabs/a hill).
 *   - Levitation resets it — the potion legitimately lifts a rider.
 *   - A decelerating rise (dy smaller than the previous tick) is treated as a
 *     normal jump arc and resets the streak, so no ordinary jump can accumulate.
 *   - minRiseTicks (default 25) consecutive non-decelerating ascending ticks AND
 *     minRise (default 4.0) blocks of total lift are both required. The strongest
 *     vanilla horse jump tops out around 5.5 blocks over ~15 ticks and is always
 *     decelerating, so it cannot reach this.
 *   - Only while ticking reliably and under 500ms ping, so lag bursts that bunch
 *     VEHICLE_MOVE packets together cannot fabricate a climb.
 */
@CheckData(name = "EntityFly", configName = "EntityFly",
        description = "Ridden mob ascending through the air (EntityFly)")
public final class EntityFly extends Check implements PacketCheck {

    private int minRiseTicks = 25;
    private double minRise = 4.0;

    private double lastY = Double.NaN;
    private double lastDy = Double.MAX_VALUE;
    private int risingTicks = 0;
    private double totalRise = 0;

    public EntityFly(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minRiseTicks = Math.max(1, config.getIntElse("EntityFly.min-rise-ticks", 25));
        minRise = config.getDoubleElse("EntityFly.min-rise", 4.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;

        PacketEntity riding = player.compensatedEntities.self.getRiding();
        // Boats are BoatFly's job. Happy ghasts fly for a living. Anything that
        // isn't a living mount (minecarts run on rails) isn't this cheat.
        if (riding == null || riding.isBoat || riding.isHappyGhast || !riding.isLivingEntity) {
            fullReset();
            return;
        }
        // Levitation legitimately lifts the whole stack.
        if (player.compensatedEntities.self.hasPotionEffect(
                com.github.retrooper.packetevents.protocol.potion.PotionTypes.LEVITATION)) {
            fullReset();
            return;
        }

        Vector3d pos = new WrapperPlayClientVehicleMove(event).getPosition();

        if (Double.isNaN(lastY)) {
            lastY = pos.getY();
            return;
        }

        double dy = pos.getY() - lastY;
        lastY = pos.getY();

        if (dy <= 0.001) { // not ascending
            reset();
            return;
        }
        if (nearWater(pos.getX(), pos.getY(), pos.getZ())) {
            reset();
            return;
        }
        // Something solid underneath means they're climbing terrain, not flying.
        if (!isAir(pos.getX(), pos.getY() - 0.6, pos.getZ())
                || !isAir(pos.getX(), pos.getY() - 1.2, pos.getZ())) {
            reset();
            return;
        }
        // A jump arc always decays under gravity. Only a powered climb doesn't.
        if (dy < lastDy - 0.001) {
            reset();
            lastDy = dy;
            return;
        }

        lastDy = dy;
        risingTicks++;
        totalRise += dy;

        if (risingTicks >= minRiseTicks && totalRise >= minRise
                && player.isTickingReliablyFor(5) && player.getTransactionPing() < 500) {
            flagAndAlert(String.format("mount=%s risingTicks=%d rise=%.2f ping=%dms",
                    riding.type == null ? "?" : riding.type.getName().getKey(),
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

    private void fullReset() {
        reset();
        lastY = Double.NaN;
    }
}
