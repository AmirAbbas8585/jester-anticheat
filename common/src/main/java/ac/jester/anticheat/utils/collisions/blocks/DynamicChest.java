package ac.jester.anticheat.utils.collisions.blocks;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionFactory;
import ac.jester.anticheat.utils.collisions.datatypes.HexCollisionBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.enums.Type;

public class DynamicChest implements CollisionFactory {
    public CollisionBox fetch(GrimPlayer player, ClientVersion version, WrappedBlockState chest, int x, int y, int z) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)
                && version.isNewerThanOrEquals(ClientVersion.V_1_13)) {
            if (chest.getTypeData() == Type.SINGLE) {
                return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
            }

            if (chest.getFacing() == BlockFace.SOUTH && chest.getTypeData() == Type.RIGHT || chest.getFacing() == BlockFace.NORTH && chest.getTypeData() == Type.LEFT) {
                return new HexCollisionBox(1.0D, 0.0D, 1.0D, 16.0D, 14.0D, 15.0D);
            } else if (chest.getFacing() == BlockFace.SOUTH && chest.getTypeData() == Type.LEFT || chest.getFacing() == BlockFace.NORTH && chest.getTypeData() == Type.RIGHT) {
                return new HexCollisionBox(0.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
            } else if (chest.getFacing() == BlockFace.WEST && chest.getTypeData() == Type.RIGHT || chest.getFacing() == BlockFace.EAST && chest.getTypeData() == Type.LEFT) {
                return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 16.0D);
            } else {
                return new HexCollisionBox(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D);
            }
        }

        if (chest.getFacing() == BlockFace.EAST || chest.getFacing() == BlockFace.WEST) {
            WrappedBlockState westState = player.compensatedWorld.getBlock(x - 1, y, z);

            if (westState.getType() == chest.getType()) {
                return new HexCollisionBox(0.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
            }

            WrappedBlockState eastState = player.compensatedWorld.getBlock(x + 1, y, z);
            if (eastState.getType() == chest.getType()) {
                return new HexCollisionBox(1.0D, 0.0D, 1.0D, 16.0D, 14.0D, 15.0D);
            }
        } else {
            WrappedBlockState northState = player.compensatedWorld.getBlock(x, y, z - 1);
            if (northState.getType() == chest.getType()) {
                return new HexCollisionBox(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D);
            }

            WrappedBlockState southState = player.compensatedWorld.getBlock(x, y, z + 1);
            if (southState.getType() == chest.getType()) {
                return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 16.0D);
            }
        }

        return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    }
}
