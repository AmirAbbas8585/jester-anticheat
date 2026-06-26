package ac.jester.anticheat.checks.impl.breaking;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AirLiquidBreak", description = "Breaking a block that cannot be broken")
public class AirLiquidBreak extends Check implements BlockBreakCheck {
    public final boolean noFireHitbox = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_15_2);
    private int lastTick;
    private boolean didLastFlag;
    // Initialize to non-null values to prevent NPE when checking for blockType properties and if position equals old position
    private @NotNull Vector3i lastBreakLoc = new Vector3i();
    private @NotNull StateType lastBlockType = StateTypes.AIR;

    public AirLiquidBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.START_DIGGING && blockBreak.action != DiggingAction.FINISHED_DIGGING)
            return;

        final StateType block = blockBreak.block.getType();

        // Fixes false from breaking kelp underwater (originally), and more
        // generally any instant-break block (hardness 0, e.g. grass, saplings,
        // redstone, tripwire): the client sends two START_DIGGING packets for
        // the same position in the same tick. AirLiquidBreak runs both times —
        // the first one legitimately breaks the block, so by the time the
        // second is processed the position is already air (or, underwater,
        // back to water). That's a same-tick processing-order artifact, not the
        // player trying to break air. Originally only block == WATER was
        // exempted; broadened to also cover plain air, since real logs (a
        // PojavLauncher player with no cheat, mild TPS jitter) showed the exact
        // same race landing on air instead of water.
        int newTick = GrimAPI.INSTANCE.getTickManager().currentTick;
        if (lastTick == newTick
                && lastBreakLoc.equals(blockBreak.position)
                && !didLastFlag
                && lastBlockType.getHardness() == 0.0F
                && lastBlockType.getBlastResistance() == 0.0F
                && (block == StateTypes.WATER || block.isAir())
        ) return;
        lastTick = newTick;
        lastBreakLoc = blockBreak.position;
        lastBlockType = block;

        // the block does not have a hitbox
        boolean invalid = (block == StateTypes.LIGHT && !(player.inventory.getHeldItem().is(ItemTypes.LIGHT) || player.inventory.getOffHand().is(ItemTypes.LIGHT)))
                || block.isAir()
                || block == StateTypes.WATER
                || block == StateTypes.LAVA
                || block == StateTypes.BUBBLE_COLUMN
                || block == StateTypes.MOVING_PISTON
                || block == StateTypes.FIRE && noFireHitbox
                // or the client claims to have broken an unbreakable block
                || block.getHardness() == -1.0f && blockBreak.action == DiggingAction.FINISHED_DIGGING;

        if (invalid && flagAndAlert("block=" + block.getName() + ", type=" + blockBreak.action) && shouldModifyPackets()) {
            didLastFlag = true;
            blockBreak.cancel();
        } else {
            didLastFlag = false;
        }
    }
}
