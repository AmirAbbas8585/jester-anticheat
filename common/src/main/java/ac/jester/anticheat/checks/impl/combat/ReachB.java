package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Reach — combat hit-distance check (independent implementation).
 *
 * Tracks every entity's position from the server's movement packets, keeping a
 * short timestamped history. When the player attacks an entity, it rewinds that
 * entity to where it was ~ping ago (what the player actually saw on screen) and
 * measures the distance from the player's eye to the entity's (generously
 * expanded) hit-box. A distance well beyond vanilla's 3.0-block attack range,
 * sustained across hits, is reach.
 *
 * Deliberately conservative: the hit-box is padded and extra latency lenience is
 * added, so only clear reach flags. Alert-only by default; calibrate from real
 * logs. Self-contained — its own entity tracking, no shared prediction state.
 */
@CheckData(name = "Reach", configName = "ReachB",
        experimental = true,
        description = "Hitting entities from beyond a plausible reach distance")
public final class ReachB extends Check implements PacketCheck {

    // entityId -> short ring history of recent positions (feet) + timestamps
    private final Map<Integer, Track> tracked = new HashMap<>();
    private static final int HISTORY = 20;

    private double maxReach = 3.4;       // vanilla attack range + a small buffer
    private double hitboxPad = 0.30;     // generous half-width padding (anti-FP)
    private double baseLenience = 0.30;  // flat tolerance on top of maxReach
    private double pingLeniencePerMs = 0.003; // extra blocks per ms of ping
    private int minConsecutive = 4;      // bad hits in a row before flagging

    private int consecutiveBad = 0;

    public ReachB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxReach = config.getDoubleElse("ReachB.max-reach", 3.4);
        hitboxPad = config.getDoubleElse("ReachB.hitbox-pad", 0.30);
        baseLenience = config.getDoubleElse("ReachB.base-lenience", 0.30);
        pingLeniencePerMs = config.getDoubleElse("ReachB.ping-lenience-per-ms", 0.003);
        minConsecutive = Math.max(1, config.getIntElse("ReachB.min-consecutive", 4));
    }

    // ── entity position tracking (server -> client) ───────────────────────────
    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
            Vector3d p = w.getPosition();
            put(w.getEntityId(), p.getX(), p.getY(), p.getZ());

        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove w = new WrapperPlayServerEntityRelativeMove(event);
            move(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());

        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation w = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            move(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());

        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport w = new WrapperPlayServerEntityTeleport(event);
            Vector3d p = w.getPosition();
            put(w.getEntityId(), p.getX(), p.getY(), p.getZ());

        } else if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities w = new WrapperPlayServerDestroyEntities(event);
            for (int id : w.getEntityIds()) tracked.remove(id);
        }
    }

    private void put(int id, double x, double y, double z) {
        tracked.computeIfAbsent(id, k -> new Track()).push(x, y, z);
    }

    private void move(int id, double dx, double dy, double dz) {
        Track t = tracked.get(id);
        if (t == null) return;
        t.push(t.x[t.last] + dx, t.y[t.last] + dy, t.z[t.last] + dz);
    }

    // ── the actual check (client -> server attack) ────────────────────────────
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Track t = tracked.get(interact.getEntityId());
        if (t == null || t.count == 0) return;

        // Rewind the target to roughly what the player saw (their ping ago).
        long when = System.currentTimeMillis() - Math.max(0, player.getTransactionPing());
        int i = t.closestTo(when);

        double eyeX = player.x;
        double eyeY = player.y + (player.isSneaking ? 1.27 : 1.62);
        double eyeZ = player.z;

        // Distance from the eye to the (padded) target box.
        double half = hitboxPad + 0.30;            // ~player half-width + pad
        double height = 1.8 + hitboxPad;           // ~player height + pad
        double dx = eyeX - clamp(eyeX, t.x[i] - half, t.x[i] + half);
        double dy = eyeY - clamp(eyeY, t.y[i], t.y[i] + height);
        double dz = eyeZ - clamp(eyeZ, t.z[i] - half, t.z[i] + half);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double allowed = maxReach + baseLenience + player.getTransactionPing() * pingLeniencePerMs;

        if (distance > allowed) {
            if (++consecutiveBad >= minConsecutive) {
                flagAndAlert(String.format("distance=%.2f max=%.2f ping=%dms",
                        distance, allowed, player.getTransactionPing()));
                consecutiveBad = 0;
            }
        } else {
            consecutiveBad = 0;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Tiny ring buffer of recent positions for one entity. */
    private static final class Track {
        final double[] x = new double[HISTORY];
        final double[] y = new double[HISTORY];
        final double[] z = new double[HISTORY];
        final long[] time = new long[HISTORY];
        int last = -1;
        int count = 0;

        void push(double px, double py, double pz) {
            last = (last + 1) % HISTORY;
            x[last] = px;
            y[last] = py;
            z[last] = pz;
            time[last] = System.currentTimeMillis();
            if (count < HISTORY) count++;
        }

        /** Index of the stored position whose timestamp is closest to {@code when}. */
        int closestTo(long when) {
            int best = last;
            long bestDiff = Long.MAX_VALUE;
            for (int n = 0; n < count; n++) {
                int idx = (last - n + HISTORY) % HISTORY;
                long diff = Math.abs(time[idx] - when);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = idx;
                }
            }
            return best;
        }
    }
}
