package ac.jester.anticheat.utils.data.interpolation;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.interpolation.stepped.SteppedEntityInterpolation;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class EntityInterpolations {
    public static EntityInterpolation create(GrimPlayer player, PacketEntity entity, SimpleCollisionBox position) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3) && usesStepped(entity.type)) {
            return new SteppedEntityInterpolation(updateInterval(entity.type), position);
        }

        return new LegacyEntityInterpolation(player, entity, position);
    }

    public static boolean usesStepped(EntityType type) {
        return EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY) && type != EntityTypes.SHULKER;
    }

    static int updateInterval(EntityType type) {
        if (type == EntityTypes.PLAYER || type == EntityTypes.ALLAY || type == EntityTypes.MANNEQUIN) {
            return 2;
        }

        return 3;
    }
}
