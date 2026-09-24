package ac.jester.anticheat.utils.data;

import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.protocol.world.BlockFace;

import java.util.List;

public class PistonData {
    public final boolean isPush;
    public final boolean hasSlimeBlock;
    public final boolean hasHoneyBlock;
    public final BlockFace direction;
    public final int lastTransactionSent;

    public int ticksOfPistonBeingAlive = 0;

    public final List<SimpleCollisionBox> boxes;

    public PistonData(BlockFace direction, List<SimpleCollisionBox> pushedBlocks, int lastTransactionSent, boolean isPush, boolean hasSlimeBlock, boolean hasHoneyBlock) {
        this.direction = direction;
        this.boxes = pushedBlocks;
        this.lastTransactionSent = lastTransactionSent;
        this.isPush = isPush;
        this.hasSlimeBlock = hasSlimeBlock;
        this.hasHoneyBlock = hasHoneyBlock;
    }

    public boolean tickIfGuaranteedFinished() {
        return ++ticksOfPistonBeingAlive >= 10;
    }
}
