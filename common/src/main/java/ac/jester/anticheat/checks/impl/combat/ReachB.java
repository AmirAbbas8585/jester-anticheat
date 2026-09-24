package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import java.util.HashMap;
import java.util.Map;

@CheckData(name = "ReachB", configName = "ReachB",
        description = "Hitting entities from beyond a plausible reach distance")
public final class ReachB extends Check implements PacketCheck {
    private final Map<Integer, Track> tracked = new HashMap<>();
    private static final int HISTORY = 20;

    private double maxReach = 3.4;
    private double hitboxPad = 0.30;
    private double baseLenience = 0.30;
    private double pingLeniencePerMs = 0.003;
    private int minConsecutive = 4;
    private long knockbackGraceMs = 600;
    private double maxPlausibleDistance = 10.0;

    private final Map<Integer, Integer> consecutiveBad = new HashMap<>();
    private long lastSelfKnockbackMs = 0;

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
        knockbackGraceMs = config.getLongElse("ReachB.knockback-grace-ms", 600);
        maxPlausibleDistance = config.getDoubleElse("ReachB.max-plausible-distance", 10.0);
    }

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
        } else if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync w = new WrapperPlayServerEntityPositionSync(event);
            Vector3d p = w.getValues().getPosition();
            put(w.getId(), p.getX(), p.getY(), p.getZ());
        } else if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities w = new WrapperPlayServerDestroyEntities(event);
            for (int id : w.getEntityIds()) {
                tracked.remove(id);
                consecutiveBad.remove(id);
            }
        } else if (type == PacketType.Play.Server.RESPAWN || type == PacketType.Play.Server.JOIN_GAME) {
            tracked.clear();
            consecutiveBad.clear();
        } else if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity w = new WrapperPlayServerEntityVelocity(event);
            if (w.getEntityId() == player.entityID) lastSelfKnockbackMs = System.currentTimeMillis();
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

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (System.currentTimeMillis() - lastSelfKnockbackMs < knockbackGraceMs) return;

        if (player.inVehicle()) return;

        PacketEntity target = player.compensatedEntities.entityMap.get(interact.getEntityId());
        if (target != null) {
            if (target.riding != null) return;
            if (target.isBoat || target.isMinecart) return;
            if (target.type == EntityTypes.CREAKING || target.type == EntityTypes.SHULKER) return;
        }

        Track t = tracked.get(interact.getEntityId());
        if (t == null || t.count == 0) return;

        long when = System.currentTimeMillis() - Math.max(0, player.getTransactionPing());
        int i = t.closestTo(when);

        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double half = hitboxPad + 0.30;
        double height = 1.8 + hitboxPad;
        double dx = eyeX - clamp(eyeX, t.x[i] - half, t.x[i] + half);
        double dy = eyeY - clamp(eyeY, t.y[i], t.y[i] + height);
        double dz = eyeZ - clamp(eyeZ, t.z[i] - half, t.z[i] + half);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double allowed = maxReach + baseLenience + player.getTransactionPing() * pingLeniencePerMs;

        if (distance > maxPlausibleDistance) return;

        int id = interact.getEntityId();
        if (distance > allowed) {
            int bad = consecutiveBad.merge(id, 1, Integer::sum);
            if (bad >= minConsecutive) {
                flagAndAlert(String.format("distance=%.2f max=%.2f ping=%dms",
                        distance, allowed, player.getTransactionPing()));
                consecutiveBad.remove(id);
            }
        } else {
            consecutiveBad.remove(id);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

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
