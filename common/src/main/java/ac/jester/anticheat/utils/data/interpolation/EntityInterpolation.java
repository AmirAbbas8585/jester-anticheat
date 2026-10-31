package ac.jester.anticheat.utils.data.interpolation;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import org.jetbrains.annotations.Nullable;

public interface EntityInterpolation {
    void begin(@Nullable PositionPath path, boolean relative, boolean hasPos, double packetX, double packetY, double packetZ, @Nullable Float yaw, @Nullable Float pitch, boolean positionSync, int transaction);

    void confirm(int transaction);

    void tick(boolean tickingReliably);

    void reset(SimpleCollisionBox position);

    SimpleCollisionBox position();

    default SimpleCollisionBox hitbox(GrimPlayer player, PacketEntity entity) {
        SimpleCollisionBox hitbox = position();
        GetBoundingBox.expandBoundingBoxByEntityDimensions(hitbox, player, entity);
        return hitbox;
    }

    default void initializeRotation(float yaw, float pitch) {
    }
}
