package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

/**
 * Fly — sustained airtime with no descent, judged independently of the
 * prediction engine.
 *
 * MovementA is the only thing that catches flight today, and it has two
 * properties that let a determined cheater keep flying: it ships alert-only, so
 * it can rubber-band but never remove anyone, and its per-tick offset bar has
 * been raised repeatedly (0.02 -> 0.025 -> 0.04) chasing false positives. A fly
 * that keeps each tick's error under that bar and simply tolerates the occasional
 * setback is invisible to it. Tuning MovementA tighter is not the answer — that
 * bar exists because real players were being flagged.
 *
 * So this check does not measure prediction error at all. It measures a vanilla
 * invariant that no fly implementation can avoid:
 *
 *   A player who is airborne, unsupported, and has no lift source MUST fall,
 *   and must keep falling faster.
 *
 * It is judged per tick: every tick the player fails to descend adds to a
 * streak, and any genuinely descending tick clears it. Two full seconds of
 * never falling is impossible without powered flight — hover, step-fly, packet
 * fly and glide all have to violate it to be useful — while a ballistic arc from
 * an explosion or a slime block breaks the streak as soon as gravity turns it
 * around. That makes the check both hard to bypass and cheap to evaluate.
 *
 * The entire difficulty is enumerating the LEGITIMATE ways to stay up, so the
 * exemption list below is the actual substance of this check:
 *   - server-granted flight (creative, spectator, /fly plugins) — the common one
 *   - elytra gliding, riptide, firework boost
 *   - levitation and slow falling potions
 *   - water, lava, bubble columns, powder snow, cobwebs, honey, scaffolding
 *   - ladders, vines and anything else climbable
 *   - riding a vehicle, or standing on top of an entity (boat, minecart, mob)
 *   - a solid block anywhere under the feet, with generous vertical slack for
 *     the server not knowing about a block the client is standing on
 *   - teleports, setbacks and the join grace window
 * Any one of these resets the counter outright, so the check only ever speaks
 * about a player who is genuinely unsupported.
 */
@CheckData(name = "Fly", configName = "Fly",
        description = "Staying airborne without falling, with no legitimate lift source")
public final class Fly extends Check implements PostPredictionCheck {

    private int minAirTicks = 40;              // 2 seconds
    private double fallingThreshold = -0.05;   // per-tick dY that counts as falling
    private double supportSearchDepth = 3.0;

    private int airTicks;
    private double lastY = Double.NaN;

    public Fly(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minAirTicks = Math.max(20, config.getIntElse("Fly.min-air-ticks", 40));
        fallingThreshold = config.getDoubleElse("Fly.falling-threshold", -0.05);
        supportSearchDepth = config.getDoubleElse("Fly.support-search-depth", 3.0);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (isExempt()) {
            reset();
            return;
        }

        double previousY = lastY;
        lastY = player.y;
        if (Double.isNaN(previousY)) return; // first sample, no delta yet

        // Measured PER TICK rather than over the whole window, and this is the
        // detail that makes the check safe. Anything launched by an explosion,
        // a fishing rod or a slime block follows a ballistic arc: gravity turns
        // its rise into a fall within a dozen ticks or so, which breaks the
        // streak long before the threshold. Comparing start-to-end height would
        // instead judge such a player at the apex of their arc and flag them.
        // Only powered flight can hold "not falling" tick after tick.
        if (player.y - previousY <= fallingThreshold) {
            airTicks = 0; // genuinely descending
            return;
        }

        if (++airTicks < minAirTicks) return;

        flagAndAlert(String.format("airTicks=%d y=%.2f dY=%.4f ping=%dms",
                airTicks, player.y, player.y - previousY, player.getTransactionPing()));
        // Restart rather than flagging every subsequent tick, so one flight
        // produces a steady trickle of violations instead of a flood.
        airTicks = 0;
    }

    /** Every legitimate reason a player can be off the ground and not falling. */
    private boolean isExempt() {
        // The server itself allows this player to fly (creative, spectator, or a
        // /fly plugin). By far the most common reason someone is legitimately
        // hovering, and the one that must never be flagged.
        if (player.canFly || player.isFlying) return true;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return true;

        // Grounded, or the client says it is — either way not sustained airtime.
        if (player.onGround || player.lastOnGround) return true;

        // Movement modes with their own physics and their own checks.
        if (player.isGliding || player.inVehicle() || player.isSwimming) return true;
        if (player.riptideSpinAttackTicks > 0) return true;
        if (player.fireworks.getMaxFireworksAppliedPossible() > 0) return true;

        // Climbing holds you up by design.
        if (player.isClimbing) return true;

        // Potions that legitimately defeat gravity.
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.LEVITATION)) return true;
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.SLOW_FALLING)) return true;

        // Fresh join / teleport / world change — position data isn't trustworthy.
        if (player.inJoinOrLoadGrace()) return true;

        // Mid-setback the position stream is not the player's own movement.
        if (player.getSetbackTeleportUtil().blockOffsets) return true;

        // Anything supporting or slowing them from below or around.
        return hasSupport();
    }

    /**
     * Looks for a block or entity that could be holding the player up.
     *
     * Deliberately generous: it searches several blocks down, because the whole
     * point is to avoid flagging someone the server merely THINKS is airborne —
     * a client standing on a block the server hasn't synced, an edge case in
     * collision, or a ghost block. A real flight happens far from any support.
     */
    private boolean hasSupport() {
        double x = player.x;
        double z = player.z;

        for (double dy = 0.0; dy <= supportSearchDepth; dy += 0.5) {
            if (isSupporting(x, player.y - dy, z)) return true;
        }
        // Also check the corners of the hitbox — standing on a block edge means
        // the centre column can be empty while the player is genuinely supported.
        double half = 0.31;
        for (double ox : new double[]{-half, half}) {
            for (double oz : new double[]{-half, half}) {
                for (double dy = 0.0; dy <= 1.5; dy += 0.5) {
                    if (isSupporting(x + ox, player.y - dy, z + oz)) return true;
                }
            }
        }

        // Standing on top of a boat, minecart or mob reads as airborne to a
        // block-only search.
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity == null || entity.isDead) continue;
            var pos = entity.trackedServerPosition.getPos();
            double dx = Math.abs(pos.getX() - x);
            double dz = Math.abs(pos.getZ() - z);
            double dy = player.y - pos.getY();
            if (dx <= 1.6 && dz <= 1.6 && dy >= -0.6 && dy <= 3.0) return true;
        }
        return false;
    }

    private boolean isSupporting(double x, double y, double z) {
        WrappedBlockState state = player.compensatedWorld.getBlock(x, y, z);
        StateType type = state.getType();
        if (type == StateTypes.AIR || type == StateTypes.CAVE_AIR || type == StateTypes.VOID_AIR) {
            return false;
        }
        // Liquids, cobwebs, powder snow, honey, scaffolding and slime all hold a
        // player up or slow their fall, so any of them means "not free-falling".
        return true;
    }

    private void reset() {
        airTicks = 0;
        lastY = Double.NaN;
    }
}
