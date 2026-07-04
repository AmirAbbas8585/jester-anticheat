package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.Map;

/**
 * TriggerBot — attacks with zero reaction time when the crosshair lands on a
 * target.
 *
 * Each client tick we track, per nearby entity, how many consecutive ticks the
 * player's crosshair has been on it (angular test against the entity's
 * tracked position). A human needs 3-5 ticks (150-250ms) between the
 * crosshair crossing onto a target and the click arriving; a triggerbot
 * clicks on the very tick of acquisition, every time.
 *
 * Flag requires minConsecutive (default 5) attacks in a row that each landed
 * within maxAcquireTicks (default 1) of crosshair acquisition. A player
 * sweeping their crosshair while spam-clicking trips one or two of these by
 * luck, but breaks the streak with the next tracked hit; the bot never does.
 */
@CheckData(name = "TriggerBot", configName = "TriggerBot",
        description = "Attacking the same tick the crosshair acquires the target (no reaction time)")
public final class TriggerBot extends Check implements PacketCheck {

    private int maxAcquireTicks = 1;
    private int minConsecutive = 5;
    private int maxPingMs = 400;

    /** entityId -> consecutive ticks the crosshair has been on the entity */
    private final Int2IntMap ticksOnTarget = new Int2IntOpenHashMap();
    private int consecutiveInstant = 0;
    // Crosshair tracking only runs while in combat (cheap for everyone else).
    // The first attack of a fight has no tracking data and is never judged.
    private int ticksSinceAttack = Integer.MAX_VALUE;
    private static final int COMBAT_WINDOW_TICKS = 200; // 10s

    public TriggerBot(GrimPlayer player) {
        super(player);
        ticksOnTarget.defaultReturnValue(0);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxAcquireTicks = config.getIntElse("TriggerBot.max-acquire-ticks", 1);
        minConsecutive = config.getIntElse("TriggerBot.min-consecutive", 5);
        maxPingMs = config.getIntElse("TriggerBot.max-ping", 400);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            if (ticksSinceAttack > COMBAT_WINDOW_TICKS) {
                // Out of combat — don't pay the per-entity math every tick
                if (!ticksOnTarget.isEmpty()) ticksOnTarget.clear();
                return;
            }
            ticksSinceAttack++;
            updateCrosshairTracking();
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        boolean wasInCombat = ticksSinceAttack <= COMBAT_WINDOW_TICKS;
        ticksSinceAttack = 0;
        if (!wasInCombat) return; // first attack of a fight — no tracking data yet

        if (!player.isTickingReliablyFor(5) || player.getTransactionPing() > maxPingMs) {
            consecutiveInstant = 0;
            return;
        }

        int onTargetTicks = ticksOnTarget.get(interact.getEntityId());
        // 0 means we never saw the crosshair on them (tracking gap) — don't judge
        if (onTargetTicks == 0) return;

        if (onTargetTicks <= maxAcquireTicks) {
            consecutiveInstant++;
            if (consecutiveInstant >= minConsecutive) {
                flagAndAlert(String.format("instant-hits=%d acquireTicks=%d ping=%dms",
                        consecutiveInstant, onTargetTicks, player.getTransactionPing()));
                consecutiveInstant = 0;
            }
        } else {
            // Tracked the target before clicking — human pattern
            consecutiveInstant = 0;
        }
    }

    private void updateCrosshairTracking() {
        // Entities that despawned never get removed below — don't let the map grow
        if (ticksOnTarget.size() > 128) ticksOnTarget.clear();

        // Look direction from yaw/pitch (Minecraft convention)
        double yawRad = Math.toRadians(player.yaw);
        double pitchRad = Math.toRadians(player.pitch);
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vector3dm eye = new Vector3dm(player.x, player.y + player.getEyeHeight(), player.z);
        final double reach = 6.0; // melee-relevant ray length
        Vector3dm end = new Vector3dm(eye.getX() + lookX * reach,
                eye.getY() + lookY * reach, eye.getZ() + lookZ * reach);

        for (Map.Entry<Integer, PacketEntity> entry : player.compensatedEntities.entityMap.entrySet()) {
            int id = entry.getKey().intValue();
            // The entity's ACTUAL interpolated hitbox — correct for every entity
            // size (cow, horse, player, ...), unlike the old fixed 0.9 centre /
            // 0.7 sphere which mis-tracked non-player mobs.
            SimpleCollisionBox box = entry.getValue().getPossibleCollisionBoxes();
            if (box == null) { ticksOnTarget.remove(id); continue; }

            // Range gate on the box centre — melee-relevant and cheap.
            double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
            double dx = cx - eye.getX(), dy = cy - eye.getY(), dz = cz - eye.getZ();
            double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 < 0.25 || dist2 > 49) {
                ticksOnTarget.remove(id);
                continue;
            }

            // Exact ray-box intersection against the real hitbox, padded slightly
            // for aim lenience — far more accurate than an angular sphere test.
            SimpleCollisionBox aimBox = box.copy().expand(0.1);
            boolean onTarget = ReachUtils.isVecInside(aimBox, eye)
                    || ReachUtils.calculateIntercept(aimBox, eye, end).first() != null;
            if (onTarget) {
                ticksOnTarget.put(id, ticksOnTarget.get(id) + 1);
            } else {
                ticksOnTarget.remove(id);
            }
        }
    }
}
