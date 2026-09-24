// This file was designed and is an original check for JesterAntiCheatPlugin
// Copyright (C) 2021 DefineOutside
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package ac.jester.anticheat.utils.data;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.NoCollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;

// You may not copy the check unless you are licensed under GPL
public class ReachInterpolationData {
    private final SimpleCollisionBox targetLocation;
    private final GrimPlayer player;
    private final PacketEntity entity;
    public SimpleCollisionBox startingLocation;
    private int interpolationStepsLowBound = 0;
    private int interpolationStepsHighBound = 0;
    private int interpolationSteps = 1;
    private boolean expandNonRelative = false;

    public ReachInterpolationData(GrimPlayer player, SimpleCollisionBox startingLocation, TrackedPosition position, PacketEntity entity) {
        final boolean unreliableTicking = !player.inVehicle() && player.canSkipTicks();

        this.startingLocation = startingLocation;
        final Vector3d pos = position.getPos();
        this.targetLocation = new SimpleCollisionBox(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z, false);
        this.player = player;
        this.entity = entity;

        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
            targetLocation.expand(0.03125);
        }

        if (entity.isBoat) {
            interpolationSteps = 10;
        } else if (entity.isMinecart) {
            interpolationSteps = 5;
        } else if (entity.type == EntityTypes.SHULKER) {
            interpolationSteps = 1;
        } else if (entity.isLivingEntity) {
            interpolationSteps = 3;
        } else {
            interpolationSteps = 1;
        }

        if (unreliableTicking) interpolationStepsHighBound = getInterpolationSteps();
    }

    public ReachInterpolationData(GrimPlayer player, SimpleCollisionBox finishedLoc, PacketEntity entity) {
        this.startingLocation = finishedLoc;
        this.targetLocation = finishedLoc;
        this.entity = entity;
        this.player = player;
    }

    public static SimpleCollisionBox combineCollisionBox(SimpleCollisionBox one, SimpleCollisionBox two) {
        double minX = Math.min(one.minX, two.minX);
        double maxX = Math.max(one.maxX, two.maxX);
        double minY = Math.min(one.minY, two.minY);
        double maxY = Math.max(one.maxY, two.maxY);
        double minZ = Math.min(one.minZ, two.minZ);
        double maxZ = Math.max(one.maxZ, two.maxZ);

        return new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static CollisionBox getOverlapHitbox(CollisionBox b1, CollisionBox b2) {
        if (b1 == NoCollisionBox.INSTANCE || b2 == NoCollisionBox.INSTANCE) {
            return NoCollisionBox.INSTANCE;
        } else if (!(b1 instanceof SimpleCollisionBox) || !(b2 instanceof SimpleCollisionBox)) {
            throw new IllegalArgumentException("Both b1 and b2 must be SimpleCollisionBox instances");
        }

        SimpleCollisionBox box1 = (SimpleCollisionBox) b1;
        SimpleCollisionBox box2 = (SimpleCollisionBox) b2;

        double overlapMinX = Math.max(box1.minX, box2.minX);
        double overlapMaxX = Math.min(box1.maxX, box2.maxX);
        double overlapMinY = Math.max(box1.minY, box2.minY);
        double overlapMaxY = Math.min(box1.maxY, box2.maxY);
        double overlapMinZ = Math.max(box1.minZ, box2.minZ);
        double overlapMaxZ = Math.min(box1.maxZ, box2.maxZ);

        if (overlapMinX > overlapMaxX || overlapMinY > overlapMaxY || overlapMinZ > overlapMaxZ) {
            return NoCollisionBox.INSTANCE;
        }

        return new SimpleCollisionBox(overlapMinX, overlapMinY, overlapMinZ, overlapMaxX, overlapMaxY, overlapMaxZ);
    }

    private int getInterpolationSteps() {
        return interpolationSteps;
    }

    public SimpleCollisionBox getPossibleLocationCombined() {
        int interpSteps = getInterpolationSteps();

        double stepMinX = (targetLocation.minX - startingLocation.minX) / (double) interpSteps;
        double stepMaxX = (targetLocation.maxX - startingLocation.maxX) / (double) interpSteps;
        double stepMinY = (targetLocation.minY - startingLocation.minY) / (double) interpSteps;
        double stepMaxY = (targetLocation.maxY - startingLocation.maxY) / (double) interpSteps;
        double stepMinZ = (targetLocation.minZ - startingLocation.minZ) / (double) interpSteps;
        double stepMaxZ = (targetLocation.maxZ - startingLocation.maxZ) / (double) interpSteps;

        SimpleCollisionBox minimumInterpLocation = new SimpleCollisionBox(
                startingLocation.minX + (interpolationStepsLowBound * stepMinX),
                startingLocation.minY + (interpolationStepsLowBound * stepMinY),
                startingLocation.minZ + (interpolationStepsLowBound * stepMinZ),
                startingLocation.maxX + (interpolationStepsLowBound * stepMaxX),
                startingLocation.maxY + (interpolationStepsLowBound * stepMaxY),
                startingLocation.maxZ + (interpolationStepsLowBound * stepMaxZ));

        for (int step = interpolationStepsLowBound + 1; step <= interpolationStepsHighBound; step++) {
            minimumInterpLocation = combineCollisionBox(minimumInterpLocation, new SimpleCollisionBox(
                    startingLocation.minX + (step * stepMinX),
                    startingLocation.minY + (step * stepMinY),
                    startingLocation.minZ + (step * stepMinZ),
                    startingLocation.maxX + (step * stepMaxX),
                    startingLocation.maxY + (step * stepMaxY),
                    startingLocation.maxZ + (step * stepMaxZ)));
        }

        return minimumInterpLocation;
    }

    public SimpleCollisionBox getPossibleHitboxCombined() {
        SimpleCollisionBox minimumInterpLocation = getPossibleLocationCombined();

        if (expandNonRelative)
            minimumInterpLocation.expand(0.03125D, 0.015625D, 0.03125D);

        GetBoundingBox.expandBoundingBoxByEntityDimensions(minimumInterpLocation, player, entity);

        return minimumInterpLocation;
    }

    public void updatePossibleStartingLocation(SimpleCollisionBox possibleLocationCombined) {
        this.startingLocation = combineCollisionBox(startingLocation, possibleLocationCombined);
    }

    public void tickMovement(boolean incrementLowBound, boolean tickingReliably) {
        if (!tickingReliably) this.interpolationStepsHighBound = getInterpolationSteps();
        if (incrementLowBound)
            this.interpolationStepsLowBound = Math.min(interpolationStepsLowBound + 1, getInterpolationSteps());
        this.interpolationStepsHighBound = Math.min(interpolationStepsHighBound + 1, getInterpolationSteps());
    }

    @Override
    public String toString() {
        return "ReachInterpolationData{" +
                "targetLocation=" + targetLocation +
                ", startingLocation=" + startingLocation +
                ", interpolationStepsLowBound=" + interpolationStepsLowBound +
                ", interpolationStepsHighBound=" + interpolationStepsHighBound +
                '}';
    }

    public void expandNonRelative() {
        expandNonRelative = true;
    }
}
