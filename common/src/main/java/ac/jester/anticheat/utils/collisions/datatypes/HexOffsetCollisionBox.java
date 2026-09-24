package ac.jester.anticheat.utils.collisions.datatypes;

import com.github.retrooper.packetevents.protocol.world.states.type.StateType;

public class HexOffsetCollisionBox extends OffsetCollisionBox {
    public HexOffsetCollisionBox(StateType block, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        super(block, minX / 16d, minY / 16d, minZ / 16d, maxX / 16d, maxY / 16d, maxZ / 16d);
    }
}
