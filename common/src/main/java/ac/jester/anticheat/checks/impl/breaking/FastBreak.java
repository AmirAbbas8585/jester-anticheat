package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import ac.jester.anticheat.utils.math.GrimMath;
import ac.jester.anticheat.utils.nmsutil.BlockBreakSpeed;
import ac.jester.anticheat.utils.viaversion.ViaVersionUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

import java.util.Set;

@CheckData(name = "FastBreak", description = "Breaking blocks too quickly")
public class FastBreak extends Check implements BlockBreakCheck {
    private static final Set<StateType> EXEMPT_STATES = Set.of();
    private final boolean clientOlderThanServer = PacketEvents.getAPI().getServerManager().getVersion().getProtocolVersion() > player.getClientVersion().getProtocolVersion();

    private double speedTolerance = 2.0;

    public FastBreak(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onReload(ConfigManager config) {
        speedTolerance = Math.max(1.0, config.getDoubleElse(getConfigName() + ".speed-tolerance", 2.0));
    }

    Vector3i targetBlockPosition = null;
    double maximumBlockDamage = 0;
    long lastFinishBreak = 0;
    long startBreak = 0;

    double blockBreakBalance = 0;
    double blockDelayBalance = 0;
    boolean lastBreakWasInstant = false;

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.START_DIGGING) {
            if (!ViaVersionUtil.isAvailable) {
                final WrappedBlockState defaultState = WrappedBlockState.getDefaultState(player.getClientVersion(), blockBreak.block.getType());
                if (defaultState.getType() == StateTypes.AIR || EXEMPT_STATES.contains(defaultState.getType())) {
                    return;
                }
            }
            WrappedBlockState block = clientOlderThanServer ? WrappedBlockState.getByGlobalId(player.getClientVersion(), player.getViaTranslatedClientBlockID(blockBreak.block.getGlobalId())) : blockBreak.block;

            startBreak = System.currentTimeMillis() - (targetBlockPosition == null ? 50 : 0);
            targetBlockPosition = blockBreak.position;

            maximumBlockDamage = BlockBreakSpeed.getBlockDamage(player, block);

            double breakDelay = System.currentTimeMillis() - lastFinishBreak;

            boolean recentCustomBlock = ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCustomBlockBreak(player);

            boolean fastTool = maximumBlockDamage > 0
                    && Math.ceil(1 / (maximumBlockDamage * speedTolerance)) * 50 < 275;

            if (breakDelay >= 275 || lastBreakWasInstant || recentCustomBlock || fastTool) {
                blockDelayBalance *= 0.9;
            } else {
                blockDelayBalance += 300 - breakDelay;
            }

            if (blockDelayBalance > 1000) {
                if (flagAndAlert("delay=" + breakDelay + "ms, type=" + blockBreak.block.getType()) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }

            clampBalance();
        }

        if (blockBreak.action == DiggingAction.FINISHED_DIGGING && targetBlockPosition != null) {
            double predictedTime = Math.ceil(1 / (maximumBlockDamage * speedTolerance)) * 50;
            double realTime = System.currentTimeMillis() - startBreak;
            double diff = predictedTime - realTime;

            clampBalance();

            boolean recentCustomBlock = ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCustomBlockBreak(player);

            if (diff < 60 || recentCustomBlock) {
                blockBreakBalance *= 0.9;
            } else {
                blockBreakBalance += diff;
            }

            if (blockBreakBalance > 1000) {
                if (flagAndAlert("diff=" + diff + "ms, balance=" + blockBreakBalance + "ms, type=" + blockBreak.block.getType()) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }

            lastBreakWasInstant = maximumBlockDamage >= 1;

            lastFinishBreak = startBreak = System.currentTimeMillis();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean flying = WrapperPlayClientPlayerFlying.isFlying(event.getPacketType());
        if ((flying || (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) && event.getPacketType() == PacketType.Play.Client.ANIMATION)) && targetBlockPosition != null) {
            maximumBlockDamage = Math.max(maximumBlockDamage, BlockBreakSpeed.getBlockDamage(player, player.compensatedWorld.getBlock(targetBlockPosition)));
        }
    }

    private void clampBalance() {
        double balance = Math.max(1000, (player.getTransactionPing()));
        blockBreakBalance = GrimMath.clamp(blockBreakBalance, -balance, balance);
        blockDelayBalance = GrimMath.clamp(blockDelayBalance, -balance, balance);
    }
}
