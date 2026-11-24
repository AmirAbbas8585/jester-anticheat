package ac.jester.anticheat.utils.data.movable;

import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.PistonData;
import ac.jester.anticheat.utils.data.movable.PistonStructure.Pos;
import ac.jester.anticheat.utils.data.movable.PistonStructure.Rules;
import ac.jester.anticheat.utils.latency.CompensatedWorld;
import ac.jester.anticheat.utils.nmsutil.PistonPushReaction.Reaction;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PistonBlockEvent {
    public static final int EXTEND = 0;
    public static final int RETRACT = 1;
    public static final int DROP = 2;

    private PistonBlockEvent() {
    }

    public static @Nullable PistonData handle(GrimPlayer player, int x, int y, int z, int action, int lastTransactionSent) {
        CompensatedWorld world = player.compensatedWorld;
        WrappedBlockState piston = world.getBlock(x, y, z);
        StateType pistonType = piston.getType();
        if (!PistonStructure.isPiston(pistonType)) return null;
        if (action != EXTEND && action != RETRACT && action != DROP) return null;

        ClientVersion version = player.getClientVersion();
        boolean modern = version.isNewerThanOrEquals(ClientVersion.V_1_13);
        boolean changeWorld = (action != DROP || modern);

        BlockFace facing = piston.getFacing();
        Pos base = new Pos(x, y, z);
        Pos head = base.relative(facing);
        Rules rules = Rules.of(version);
        Push push = new Push();

        if (action == EXTEND) {
            PistonStructure structure = new PistonStructure(world, rules, base, facing, true);
            if (structure.resolve()) {
                push.add(structure, structure.toPush, facing);
                if (changeWorld) {
                    moveBlocks(world, structure, head, facing, true);
                    WrappedBlockState extended = piston.clone();
                    extended.setExtended(true);
                    world.updateBlock(x, y, z, extended.getGlobalId());
                }
            } else {
                push.add(structure, straightLine(structure, head, facing), facing);
            }
            push.boxes.add(box(head));
            return push.toData(base, facing, lastTransactionSent, true);
        }

        BlockFace pull = facing.getOppositeFace();
        push.boxes.add(box(head));
        if (changeWorld) {
            world.updateBlock(base.x(), base.y(), base.z(), movingPiston(facing).getGlobalId());
        }

        if (pistonType != StateTypes.STICKY_PISTON) {
            if (changeWorld) setAir(world, head);
            return push.toData(base, pull, lastTransactionSent, false);
        }

        PistonStructure structure = new PistonStructure(world, rules, base, facing, false);
        Pos front = base.relative(facing, 2);
        WrappedBlockState pulled = structure.state(front);
        boolean pulls = action == RETRACT
                && !pulled.getType().isAir()
                && structure.isPushable(pulled, front, pull, false, facing)
                && (structure.reaction(pulled.getType()) == Reaction.NORMAL || PistonStructure.isPiston(pulled.getType()));
        if (pulls) {
            if (changeWorld) setAir(world, head);
            if (structure.resolve()) {
                push.add(structure, structure.toPush, pull);
                if (changeWorld) moveBlocks(world, structure, head, pull, false);
            }
        } else if (changeWorld && modern) {
            setAir(world, head);
        }
        return push.toData(base, pull, lastTransactionSent, false);
    }

    private static void moveBlocks(CompensatedWorld world, PistonStructure structure, Pos head, BlockFace direction, boolean extending) {
        Set<Pos> vacated = new LinkedHashSet<>(structure.toPush);
        for (Pos destroyed : structure.toDestroy) {
            setAir(world, destroyed);
        }

        WrappedBlockState moving = movingPiston(direction);
        for (int i = structure.toPush.size() - 1; i >= 0; i--) {
            Pos destination = structure.toPush.get(i).relative(direction);
            vacated.remove(destination);
            world.updateBlock(destination.x(), destination.y(), destination.z(), moving.getGlobalId());
        }

        if (extending) {
            vacated.remove(head);
            world.updateBlock(head.x(), head.y(), head.z(), moving.getGlobalId());
        }

        for (Pos pos : vacated) {
            setAir(world, pos);
        }
    }

    private static WrappedBlockState movingPiston(BlockFace facing) {
        WrappedBlockState state = WrappedBlockState.getDefaultState(CompensatedWorld.blockVersion, StateTypes.MOVING_PISTON).clone();
        state.setFacing(facing);
        return state;
    }

    private static void setAir(CompensatedWorld world, Pos pos) {
        world.updateBlock(pos.x(), pos.y(), pos.z(), WrappedBlockState.getByGlobalId(CompensatedWorld.blockVersion, 0).getGlobalId());
    }

    private static List<Pos> straightLine(PistonStructure structure, Pos from, BlockFace direction) {
        List<Pos> line = new ArrayList<>();
        Pos pos = from;
        while (line.size() < PistonStructure.MAX_PUSH_DEPTH && !structure.state(pos).getType().isAir()) {
            line.add(pos);
            pos = pos.relative(direction);
        }
        return line;
    }

    private static SimpleCollisionBox box(Pos pos) {
        return new SimpleCollisionBox(pos.x(), pos.y(), pos.z());
    }

    private static final class Push {
        final List<SimpleCollisionBox> boxes = new ArrayList<>();
        boolean slime;
        boolean honey;

        void add(PistonStructure structure, List<Pos> positions, BlockFace direction) {
            for (Pos pos : positions) {
                boxes.add(box(pos));
                boxes.add(box(pos.relative(direction)));
                StateType type = structure.state(pos).getType();
                slime |= type == StateTypes.SLIME_BLOCK;
                honey |= type == StateTypes.HONEY_BLOCK;
            }
        }

        PistonData toData(Pos base, BlockFace direction, int lastTransactionSent, boolean isPush) {
            return new PistonData(direction, boxes, lastTransactionSent, isPush, slime, honey);
        }
    }
}
