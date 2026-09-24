package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.impl.badpackets.BadPacketsN;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.platform.api.entity.GrimEntity;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngine;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineElytra;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineNormal;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineWater;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.chunks.Column;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.data.SetBackData;
import ac.jester.anticheat.utils.data.TeleportAcceptData;
import ac.jester.anticheat.utils.data.TeleportData;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.VelocityData;
import ac.jester.anticheat.utils.math.GrimMath;
import ac.jester.anticheat.utils.math.Location;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.math.VectorUtils;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SetbackTeleportUtil extends Check implements PostPredictionCheck {
    public final ConcurrentLinkedQueue<TeleportData> pendingTeleports = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();
    public boolean hasAcceptedSpawnTeleport = false;
    public boolean blockOffsets = false;
    public SetbackPosWithVector lastKnownGoodPosition;
    public boolean isSendingSetback = false;
    public int cheatVehicleInterpolationDelay = 0;
    @Getter
    private SetBackData requiredSetBack = null;
    private long lastWorldResync = 0;

    public SetbackTeleportUtil(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        Vector3dm afterTickFriction = player.clientVelocity.clone();

        if (predictionComplete.getData().getSetback() != null) {
            if (cheatVehicleInterpolationDelay > 0) cheatVehicleInterpolationDelay = 10;
            lastKnownGoodPosition = new SetbackPosWithVector(new Vector3d(player.x, player.y, player.z), afterTickFriction);
        } else if (requiredSetBack == null || requiredSetBack.isComplete()) {
            cheatVehicleInterpolationDelay--;
            lastKnownGoodPosition = new SetbackPosWithVector(new Vector3d(player.x, player.y, player.z), afterTickFriction);
        }

        if (requiredSetBack != null) requiredSetBack.tick();
    }

    public void executeForceResync() {
        if (player.gamemode == GameMode.SPECTATOR || player.disableGrim)
            return;
        if (lastKnownGoodPosition == null) return;
        blockMovementsUntilResync(true, true);
    }

    public void executeNonSimulatingForceResync() {
        if (player.gamemode == GameMode.SPECTATOR || player.disableGrim)
            return;
        if (lastKnownGoodPosition == null) return;
        blockMovementsUntilResync(false, true);
    }

    public void executeNonSimulatingSetback() {
        if (player.gamemode == GameMode.SPECTATOR || player.disableGrim)
            return;
        if (lastKnownGoodPosition == null) return;
        blockMovementsUntilResync(false, false);
    }

    public boolean executeViolationSetback() {
        if (isExempt()) return false;
        blockMovementsUntilResync(true, false);
        return true;
    }

    private boolean isExempt() {
        if (lastKnownGoodPosition == null) return true;
        if (player.disableGrim) return true;
        if (ac.jester.anticheat.manager.BedrockPolicy.isEnabled() && player.isBedrock()) return true;
        return player.platformPlayer != null && player.noSetbackPermission;
    }

    private void simulateFriction(Vector3dm vector) {
        if (player.wasTouchingWater) {
            PredictionEngineWater.staticVectorEndOfTick(player, vector, 0.8F, player.gravity, true);
        } else if (player.wasTouchingLava) {
            vector.multiply(0.5D);
            if (player.hasGravity)
                vector.add(0.0D, -player.gravity / 4.0D, 0.0D);
        } else if (player.isGliding) {
            PredictionEngineElytra.getElytraMovement(player, vector, ReachUtils.getLook(player, player.yaw, player.pitch)).multiply(player.stuckSpeedMultiplier).multiply(0.99F, 0.98F, 0.99F);
            vector.setY(vector.getY() - 0.05);
        } else {
            PredictionEngineNormal.staticVectorEndOfTick(player, vector);
        }

        vector.multiply(player.stuckSpeedMultiplier);

        new PredictionEngine().applyMovementThreshold(player, new HashSet<>(Collections.singletonList(new VectorData(vector, VectorData.VectorType.BestVelPicked))));
    }

    private void blockMovementsUntilResync(boolean simulateNextTickPosition, boolean isResync) {
        if (requiredSetBack == null) return;
        if (player.platformPlayer != null && player.noSetbackPermission)
            return;
        requiredSetBack.setPlugin(false);
        if (isPendingSetback()) return;

        if (System.currentTimeMillis() - lastWorldResync > 5 * 1000) {
            player.resyncPositions(player.boundingBox.copy().expand(1));
            lastWorldResync = System.currentTimeMillis();
        }

        Vector3dm clientVel = lastKnownGoodPosition.vector.clone();

        Pair<VelocityData, Vector3dm> futureKb = player.checkManager.getKnockbackHandler().getFutureKnockback();
        VelocityData futureExplosion = player.checkManager.getExplosionHandler().getFutureExplosion();

        if (futureKb.first() != null && !futureKb.first().isSetback) {
            clientVel = futureKb.second();
        }

        if (futureExplosion != null && (futureKb.first() == null
                || (futureKb.first().transaction < futureExplosion.transaction && !futureKb.first().isSetback))) {
            clientVel.add(futureExplosion.vector);
        }

        Vector3d position = lastKnownGoodPosition.pos;

        SimpleCollisionBox oldBB = player.boundingBox;
        player.boundingBox = GetBoundingBox.getPlayerBoundingBox(player, position.getX(), position.getY(), position.getZ());

        if (simulateNextTickPosition) {
            Vector3dm collide = Collisions.collide(player, clientVel.getX(), clientVel.getY(), clientVel.getZ());

            position = position.withX(position.getX() + collide.getX());
            position = position.withY(position.getY() + collide.getY());
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
                position = position.withY(position.getY() + SimpleCollisionBox.COLLISION_EPSILON);
            }
            position = position.withZ(position.getZ() + collide.getZ());

            if (clientVel.getX() != collide.getX()) clientVel.setX(0);
            if (clientVel.getY() != collide.getY()) clientVel.setY(0);
            if (clientVel.getZ() != collide.getZ()) clientVel.setZ(0);

            simulateFriction(clientVel);
        }

        player.boundingBox = oldBB;

        if (!hasAcceptedSpawnTeleport || player.isFlying)
            clientVel = null;

        if (isResync) {
            blockOffsets = true;
        }

        SetBackData data = new SetBackData(new TeleportData(position, null, RelativeFlag.YAW.or(RelativeFlag.PITCH), player.lastTransactionSent.get(), 0, 0f, 0f), player.yaw, player.pitch, clientVel, player.inVehicle(), false);
        sendSetback(data);
    }

    private void sendSetback(SetBackData data) {
        isSendingSetback = true;
        Vector3d position = data.getTeleportData().getLocation();

        try {
            if (player.inVehicle()) {
                int vehicleId = player.getRidingVehicleId();
                if (player.compensatedEntities.serverPlayerVehicle != null) {
                    if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
                        player.user.sendPacket(new WrapperPlayServerSetPassengers(vehicleId, new int[2]));
                    } else {
                        player.user.sendPacket(new WrapperPlayServerAttachEntity(vehicleId, -1, false));
                    }

                    player.user.sendPacket(new WrapperPlayServerEntityTeleport(vehicleId, new Vector3d(position.getX(), position.getY(), position.getZ()), player.yaw % 360, 0, false));
                    player.getSetbackTeleportUtil().cheatVehicleInterpolationDelay = Integer.MAX_VALUE;

                    GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player.platformPlayer, GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                        if (player.platformPlayer != null) {
                            GrimEntity vehicle = player.platformPlayer.getVehicle();
                            if (vehicle != null) {
                                vehicle.eject();
                            }
                        }
                    }, null, 0);
                }
            }

            double y = position.getY();
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_7_10)) {
                y += 1.62;
            }

            player.sendTransaction();

            int teleportId = random.nextInt() | Integer.MIN_VALUE;
            data.setPlugin(false);
            data.getTeleportData().setTeleportId(teleportId);
            data.getTeleportData().setTransaction(player.lastTransactionSent.get());

            addSentTeleport(new Location(null, position.getX(), y, position.getZ(), player.yaw % 360, player.pitch % 360),
                    null, data.getTeleportData().getTransaction(), RelativeFlag.YAW.or(RelativeFlag.PITCH), false, teleportId);
            requiredSetBack = data;
            PacketEvents.getAPI().getProtocolManager().sendPacketSilently(player.user.getChannel(), new WrapperPlayServerPlayerPositionAndLook(position.getX(), position.getY(), position.getZ(), 0, 0, data.getTeleportData().getFlags().getMask(), teleportId, false));
            player.sendTransaction();

            if (data.getVelocity() != null && data.getVelocity().lengthSquared() > 0) {
                player.user.sendPacket(new WrapperPlayServerEntityVelocity(player.entityID, new Vector3d(data.getVelocity().getX(), data.getVelocity().getY(), data.getVelocity().getZ())));
            }
        } finally {
            isSendingSetback = false;
        }
    }

    public TeleportAcceptData checkTeleportQueue(double x, double y, double z, float yaw, float pitch) {
        TeleportAcceptData teleportData = new TeleportAcceptData();

        TeleportData teleportPos;
        while ((teleportPos = pendingTeleports.peek()) != null) {
            double trueTeleportX = (teleportPos.isRelativeX() ? player.x : 0) + teleportPos.getLocation().getX();
            double trueTeleportY = (teleportPos.isRelativeY() ? player.y : 0) + teleportPos.getLocation().getY();
            double trueTeleportZ = (teleportPos.isRelativeZ() ? player.z : 0) + teleportPos.getLocation().getZ();

            Vector3d clamped = VectorUtils.clampVector(new Vector3d(trueTeleportX, trueTeleportY, trueTeleportZ));
            double threshold = teleportPos.isRelativePos() ? player.getMovementThreshold() : 0;
            boolean closeEnoughY = Math.abs(clamped.getY() - y) <= 1e-7 + threshold;

            boolean rotationMatches = true;
            if (!teleportPos.isRelativeYaw()) {
                float expectedYaw = teleportPos.getYaw() % 360f;
                float clientYaw = yaw % 360f;
                rotationMatches = Math.abs(expectedYaw - clientYaw) < 1f
                        || Math.abs(Math.abs(expectedYaw - clientYaw) - 360f) < 1f;
            }
            if (rotationMatches && !teleportPos.isRelativePitch()) {
                float expectedPitch = teleportPos.getPitch();
                rotationMatches = Math.abs(expectedPitch - pitch) < 1f;
            }

            if (player.lastTransactionReceived.get() == teleportPos.getTransaction() && Math.abs(clamped.getX() - x) <= threshold && closeEnoughY && Math.abs(clamped.getZ() - z) <= threshold && rotationMatches) {
                pendingTeleports.poll();
                hasAcceptedSpawnTeleport = true;
                blockOffsets = false;

                if (requiredSetBack != null && requiredSetBack.getTeleportData().getTransaction() == teleportPos.getTransaction()) {
                    teleportData.setSetback(requiredSetBack);
                    requiredSetBack.setComplete(true);
                }

                teleportData.setTeleportData(teleportPos);
                teleportData.setTeleport(true);
                break;
            } else if (player.lastTransactionReceived.get() > teleportPos.getTransaction()) {
                if (!player.inJoinOrLoadGrace()) {
                    player.checkManager.getCheck(BadPacketsN.class).flagAndAlert();
                }
                pendingTeleports.poll();
                requiredSetBack.setPlugin(false);
                if (pendingTeleports.isEmpty()) {
                    sendSetback(requiredSetBack);
                }
                continue;
            }
            break;
        }

        return teleportData;
    }

    public boolean checkVehicleTeleportQueue(double x, double y, double z) {
        int lastTransaction = player.lastTransactionReceived.get();

        while (true) {
            Pair<Integer, Vector3d> teleportPos = player.vehicleData.vehicleTeleports.peek();
            if (teleportPos == null) break;
            if (lastTransaction < teleportPos.first()) {
                break;
            }

            Vector3d position = teleportPos.second();
            if (position.getX() == x && position.getY() == y && position.getZ() == z) {
                player.vehicleData.vehicleTeleports.poll();

                return true;
            } else if (lastTransaction > teleportPos.first() + 1) {
                player.vehicleData.vehicleTeleports.poll();

                continue;
            }

            break;
        }

        return false;
    }

    public boolean shouldBlockMovement() {
        return insideUnloadedChunk() || blockOffsets || (requiredSetBack != null && !requiredSetBack.isComplete());
    }

    private boolean isPendingSetback() {
        if (requiredSetBack != null && (requiredSetBack.getTeleportData().isRelativeX() || requiredSetBack.getTeleportData().isRelativeY() || requiredSetBack.getTeleportData().isRelativeZ())) {
            return false;
        }
        return requiredSetBack != null && !requiredSetBack.isComplete();
    }

    public boolean insideUnloadedChunk() {
        Column column = player.compensatedWorld.getChunk(GrimMath.floor(player.x) >> 4, GrimMath.floor(player.z) >> 4);

        return !player.disableGrim && (column == null || column.transaction() >= player.lastTransactionReceived.get() ||
                !player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport);
    }

    public void addSentTeleport(Location position, @Nullable Vector3d velocity, int transaction, RelativeFlag flags, boolean plugin, int teleportId) {
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2)) {
            velocity = null;
        }

        TeleportData data = new TeleportData(new Vector3d(position.getX(), position.getY(), position.getZ()), velocity, flags, transaction, teleportId, position.getYaw(), position.getPitch());
        pendingTeleports.add(data);

        Vector3d safePosition = new Vector3d(position.getX(), position.getY(), position.getZ());

        if (flags.has(RelativeFlag.X)) {
            safePosition = safePosition.withX(safePosition.getX() + lastKnownGoodPosition.pos.getX());
        }

        if (flags.has(RelativeFlag.Y)) {
            safePosition = safePosition.withY(safePosition.getY() + lastKnownGoodPosition.pos.getY());
        }

        if (flags.has(RelativeFlag.Z)) {
            safePosition = safePosition.withZ(safePosition.getZ() + lastKnownGoodPosition.pos.getZ());
        }

        data = new TeleportData(safePosition, velocity, RelativeFlag.YAW.or(RelativeFlag.PITCH), transaction, teleportId, 0f, 0f);
        requiredSetBack = new SetBackData(data, player.yaw, player.pitch, null, false, plugin);

        this.lastKnownGoodPosition = new SetbackPosWithVector(safePosition, new Vector3dm());
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class SetbackPosWithVector {
        private final Vector3d pos;
        private Vector3dm vector;
    }
}
