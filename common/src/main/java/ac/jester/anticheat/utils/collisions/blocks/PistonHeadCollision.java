package ac.jester.anticheat.utils.collisions.blocks;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionFactory;
import ac.jester.anticheat.utils.collisions.datatypes.ComplexCollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.HexCollisionBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

public class PistonHeadCollision implements CollisionFactory {
    @Override
    public CollisionBox fetch(GrimPlayer player, ClientVersion version, WrappedBlockState block, int x, int y, int z) {
        double longAmount = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13) && block.isShort() ? 0 : 4;

        if (version.isOlderThanOrEquals(ClientVersion.V_1_12_2) || PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_12_2))
            longAmount = 4;

        if (version.isOlderThan(ClientVersion.V_1_9) || PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9))
            longAmount = 0;

        return switch (block.getFacing()) {
            case UP -> new ComplexCollisionBox(2,
                    new HexCollisionBox(0, 12, 0, 16, 16, 16),
                    new HexCollisionBox(6, 0 - longAmount, 6, 10, 12, 10));
            case NORTH -> new ComplexCollisionBox(2,
                    new HexCollisionBox(0, 0, 0, 16, 16, 4),
                    new HexCollisionBox(6, 6, 4, 10, 10, 16 + longAmount));
            case SOUTH -> {
                if (version.isOlderThanOrEquals(ClientVersion.V_1_8))
                    yield new ComplexCollisionBox(2,
                            new HexCollisionBox(0, 0, 12, 16, 16, 16),
                            new HexCollisionBox(4, 6, 0, 12, 10, 12));

                yield new ComplexCollisionBox(2,
                        new HexCollisionBox(0, 0, 12, 16, 16, 16),
                        new HexCollisionBox(6, 6, 0 - longAmount, 10, 10, 12));
            }
            case WEST -> {
                if (version.isOlderThanOrEquals(ClientVersion.V_1_8))
                    yield new ComplexCollisionBox(2,
                            new HexCollisionBox(0, 0, 0, 4, 16, 16),
                            new HexCollisionBox(6, 4, 4, 10, 12, 16));

                yield new ComplexCollisionBox(2,
                        new HexCollisionBox(0, 0, 0, 4, 16, 16),
                        new HexCollisionBox(4, 6, 6, 16 + longAmount, 10, 10));
            }
            case EAST -> new ComplexCollisionBox(2,
                    new HexCollisionBox(12, 0, 0, 16, 16, 16),
                    new HexCollisionBox(0 - longAmount, 6, 4, 12, 10, 12));
            default -> new ComplexCollisionBox(2,
                    new HexCollisionBox(0, 0, 0, 16, 4, 16),
                    new HexCollisionBox(6, 4, 6, 10, 16 + longAmount, 10));
        };
    }
}
