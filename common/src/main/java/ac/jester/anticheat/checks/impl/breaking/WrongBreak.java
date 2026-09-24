package ac.jester.anticheat.checks.impl.breaking;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;

import static ac.jester.anticheat.utils.nmsutil.BlockBreakSpeed.getBlockDamage;

@CheckData(name = "WrongBreak")
public class WrongBreak extends Check implements BlockBreakCheck {
    private final int exemptedY = player.getClientVersion().isOlderThan(ClientVersion.V_1_8) ? 255 : (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14) ? -1 : 4095);
    private boolean lastBlockWasInstantBreak = false;
    private Vector3i lastBlock, lastCancelledBlock, lastLastBlock = null;

    public WrongBreak(final GrimPlayer player) {
        super(player);
    }

    private boolean shouldExempt(final WrappedBlockState block, int yPos) {
        if (lastLastBlock != null || lastBlock == null)
            return false;

        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) && yPos != exemptedY)
            return false;

        return player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) || getBlockDamage(player, block) < 1;
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.START_DIGGING) {
            final Vector3i pos = blockBreak.position;

            lastBlockWasInstantBreak = getBlockDamage(player, blockBreak.block) >= 1;
            lastCancelledBlock = null;
            lastLastBlock = lastBlock;
            lastBlock = pos;
        }

        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) {
            final Vector3i pos = blockBreak.position;

            if (!shouldExempt(blockBreak.block, pos.y) && !pos.equals(lastBlock)) {
                if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4) || (!lastBlockWasInstantBreak && pos.equals(lastCancelledBlock))) {
                    if (flagAndAlert("action=CANCELLED_DIGGING" + ", last=" + MessageUtil.toUnlabledString(lastBlock) + ", pos=" + MessageUtil.toUnlabledString(pos))) {
                        if (shouldModifyPackets()) {
                            blockBreak.cancel();
                        }
                    }
                }
            }

            lastCancelledBlock = pos;
            lastLastBlock = null;
            lastBlock = null;
            return;
        }

        if (blockBreak.action == DiggingAction.FINISHED_DIGGING) {
            final Vector3i pos = blockBreak.position;

            if (!pos.equals(lastCancelledBlock) && (!lastBlockWasInstantBreak || player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4)) && !pos.equals(lastBlock)) {
                if (flagAndAlert("action=FINISHED_DIGGING" + ", last=" + MessageUtil.toUnlabledString(lastBlock) + ", pos=" + MessageUtil.toUnlabledString(pos))) {
                    if (shouldModifyPackets()) {
                        blockBreak.cancel();
                    }
                }
            }

            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14_4)) {
                lastCancelledBlock = null;
                lastLastBlock = null;
                lastBlock = null;
            }
        }
    }
}
