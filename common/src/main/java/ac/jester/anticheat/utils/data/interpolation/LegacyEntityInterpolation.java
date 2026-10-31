package ac.jester.anticheat.utils.data.interpolation;

import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.ReachInterpolationData;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.jetbrains.annotations.Nullable;

public final class LegacyEntityInterpolation implements EntityInterpolation {
    private final GrimPlayer player;
    private final PacketEntity entity;

    private ReachInterpolationData oldPacketLocation;
    private ReachInterpolationData newPacketLocation;

    private float interpolationTargetXRot, interpolationTargetYRot;

    public LegacyEntityInterpolation(GrimPlayer player, PacketEntity entity, SimpleCollisionBox position) {
        this.player = player;
        this.entity = entity;
        this.newPacketLocation = new ReachInterpolationData(player, position, entity.trackedServerPosition, entity);
    }

    @Override
    public void begin(@Nullable PositionPath path, boolean relative, boolean hasPos,
                      double relX, double relY, double relZ,
                      @Nullable Float packetXRot, @Nullable Float packetYRot, boolean positionSync, int transaction) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_9)
                && newPacketLocation.restatesTarget(entity.trackedServerPosition.getPos(),
                packetXRot, packetYRot, interpolationTargetXRot, interpolationTargetYRot)) {
            return;
        }

        if (packetXRot != null && packetYRot != null) {
            this.interpolationTargetXRot = packetXRot;
            this.interpolationTargetYRot = packetYRot;
        }

        this.oldPacketLocation = newPacketLocation;
        if (!hasPos &&
                ((player.getClientVersion().isOlderThan(ClientVersion.V_1_21_9) && player.getClientVersion().isNewerThan(ClientVersion.V_1_21_4)) ||
                        (player.getClientVersion().isOlderThan(ClientVersion.V_1_20_2) && player.getClientVersion().isNewerThan(ClientVersion.V_1_14_4)))
        ) {
            this.newPacketLocation = new ReachInterpolationData(
                    player,
                    oldPacketLocation.getPossibleLocationCombined(),
                    entity
            );
        } else {
            this.newPacketLocation = new ReachInterpolationData(player, oldPacketLocation.getPossibleLocationCombined(), entity.trackedServerPosition, entity);
        }

        if (hasPos && !relative && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_16_1)) {
            SimpleCollisionBox clientArea = newPacketLocation.getPossibleLocationCombined();
            if (clientArea.distanceX(relX) < 0.03125D
                    && clientArea.distanceY(relY) < 0.015625D
                    && clientArea.distanceZ(relZ) < 0.03125D) {
                newPacketLocation.expandNonRelative();
            }
        }
    }

    @Override
    public void confirm(int transaction) {
        this.oldPacketLocation = null;
    }

    @Override
    public void tick(boolean tickingReliably) {
        newPacketLocation.tickMovement(oldPacketLocation == null, tickingReliably);

        if (oldPacketLocation != null) {
            oldPacketLocation.tickMovement(true, tickingReliably);
            newPacketLocation.updatePossibleStartingLocation(oldPacketLocation.getPossibleLocationCombined());
        }
    }

    @Override
    public void reset(SimpleCollisionBox box) {
        this.newPacketLocation = new ReachInterpolationData(player, box, entity);
    }

    @Override
    public SimpleCollisionBox position() {
        if (oldPacketLocation == null) {
            return newPacketLocation.getPossibleLocationCombined();
        }

        return ReachInterpolationData.combineCollisionBox(oldPacketLocation.getPossibleLocationCombined(), newPacketLocation.getPossibleLocationCombined());
    }

    @Override
    public SimpleCollisionBox hitbox(GrimPlayer player, PacketEntity entity) {
        if (oldPacketLocation == null) {
            return newPacketLocation.getPossibleHitboxCombined();
        }

        return ReachInterpolationData.combineCollisionBox(oldPacketLocation.getPossibleHitboxCombined(), newPacketLocation.getPossibleHitboxCombined());
    }
}
