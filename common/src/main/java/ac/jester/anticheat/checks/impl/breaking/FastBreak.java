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

// Based loosely off of Hawk BlockBreakSpeedSurvival
// Also based loosely off of NoCheatPlus FastBreak
// Also based off minecraft wiki: https://minecraft.wiki/w/Breaking#Instant_breaking
@CheckData(name = "FastBreak", description = "Breaking blocks too quickly")
public class FastBreak extends Check implements BlockBreakCheck {

    // For some reason these states flag and I don't know why.
    // Better to just exempt to not annoy legit players.
    private static final Set<StateType> EXEMPT_STATES = Set.of();
    private final boolean clientOlderThanServer = PacketEvents.getAPI().getServerManager().getVersion().getProtocolVersion() > player.getClientVersion().getProtocolVersion();

    // How much faster than vanilla-predicted mining to tolerate. Custom enchant
    // plugins (Efficiency above the vanilla max, CrazyEnchantments Haste/Blast,
    // ...) make players mine faster than this check can predict from vanilla
    // data, which false-flags them. 2.0 = allow up to twice the predicted speed.
    // Raise it if custom enchants still flag; lower toward 1.0 on vanilla-only
    // servers for stricter detection.
    private double speedTolerance = 2.0;

    public FastBreak(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onReload(ConfigManager config) {
        speedTolerance = Math.max(1.0, config.getDoubleElse(getConfigName() + ".speed-tolerance", 2.0));
    }

    // Extra mining-speed factor from a custom-enchant plugin (e.g. CrazyEnchantments
    // Haste) read off the held tool by the hook — applied to the predicted speed so
    // the actual enchant level is accounted for instead of a blanket loosening.
    private double customMiningFactor() {
        int lvl = ac.jester.anticheat.hooks.ExemptionProvider.safe().getCustomMiningSpeedLevel(player);
        return lvl > 0 ? 1 + 0.2 * (lvl + 1) : 1.0;
    }

    // The block the player is currently breaking
    Vector3i targetBlockPosition = null;
    // The maximum amount of damage the player deals to the block
    //
    double maximumBlockDamage = 0;
    // The last time a finish digging packet was sent, to enforce 0.3-second delay after non-instabreak
    long lastFinishBreak = 0;
    // The time the player started to break the block, to know how long the player waited until they finished breaking the block
    long startBreak = 0;

    // The buffer to this check
    double blockBreakBalance = 0;
    double blockDelayBalance = 0;
    // True if the LAST completed break was an instant-break block (hardness 0,
    // e.g. grass/flowers/redstone/tripwire/torches). Vanilla has NO minimum delay
    // between separate instant-break sessions — a player can legitimately spam the
    // same spot every tick (no aiming time needed). This matters a lot in regions
    // where a protection plugin (WorldGuard, etc.) denies the break and the block
    // resyncs back: the player keeps re-clicking the exact same instant block
    // rapidly, which is indistinguishable from FastBreak under the old 275ms
    // assumption but is completely legitimate.
    boolean lastBreakWasInstant = false;

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.START_DIGGING) {
            if (!ViaVersionUtil.isAvailable) {
                // Exempt all blocks that do not exist in the player version
                final WrappedBlockState defaultState = WrappedBlockState.getDefaultState(player.getClientVersion(), blockBreak.block.getType());
                if (defaultState.getType() == StateTypes.AIR || EXEMPT_STATES.contains(defaultState.getType())) {
                    return;
                }
            }
            // If client is older than the server, fetch block client actually sees from via
            // otherwise just return the server-side block (since if client is >= server version the block is guaranteed to exist in client version)
            // TODO this lazy loads PacketEvents mappings for older versions for clients on versions older than the servers, increasing memory usage
            //  * its the only thing we use non-native mappings for behind ViaVersion
            //  * can we translate back "up" to server version and run check against server version to avoid loading older registries?
            WrappedBlockState block = clientOlderThanServer ? WrappedBlockState.getByGlobalId(player.getClientVersion(), player.getViaTranslatedClientBlockID(blockBreak.block.getGlobalId())) : blockBreak.block;

            startBreak = System.currentTimeMillis() - (targetBlockPosition == null ? 50 : 0); // ???
            targetBlockPosition = blockBreak.position;

            maximumBlockDamage = BlockBreakSpeed.getBlockDamage(player, block);

            double breakDelay = System.currentTimeMillis() - lastFinishBreak;

            // ItemsAdder custom blocks can have break timing that doesn't match
            // vanilla hardness assumptions (and a denied/resyncing custom block
            // has the same rapid-retry pattern as a denied vanilla instant block).
            boolean recentCustomBlock = ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCustomBlockBreak(player);

            // A high-speed tool/enchant (vanilla Efficiency+Haste, or a custom
            // enchant) legitimately breaks this block in under the assumed minimum
            // aiming delay — so don't penalise the short gap between such breaks.
            boolean fastTool = maximumBlockDamage > 0
                    && Math.ceil(1 / (maximumBlockDamage * speedTolerance * customMiningFactor())) * 50 < 275;

            if (breakDelay >= 275 || lastBreakWasInstant || recentCustomBlock || fastTool) { // Reduce buffer if "close enough", or the last break was instant/custom/fast (no minimum delay applies)
                blockDelayBalance *= 0.9;
            } else { // Otherwise, increase buffer
                blockDelayBalance += 300 - breakDelay;
            }

            if (blockDelayBalance > 1000) { // If more than a second of advantage
                if (flagAndAlert("delay=" + breakDelay + "ms, type=" + blockBreak.block.getType()) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }

            clampBalance();
        }

        if (blockBreak.action == DiggingAction.FINISHED_DIGGING && targetBlockPosition != null) {
            // speedTolerance inflates the predicted mining damage so breaking
            // faster than vanilla predicts (custom enchants) isn't a violation.
            double predictedTime = Math.ceil(1 / (maximumBlockDamage * speedTolerance * customMiningFactor())) * 50;
            double realTime = System.currentTimeMillis() - startBreak;
            double diff = predictedTime - realTime;

            clampBalance();

            // maximumBlockDamage is computed from the vanilla block state, which
            // doesn't know an ItemsAdder custom block (often a re-skinned vanilla
            // block under the hood) can have a totally different configured
            // hardness — so predictedTime can be way off for a perfectly legit break.
            boolean recentCustomBlock = ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCustomBlockBreak(player);

            // Real logs showed a legit player breaking "lectern" with a very
            // consistent diff of ~26-67ms (averaging right around one tick,
            // 50ms) at completely normal TPS (19.93-20.04) — a textbook
            // off-by-one-tick rounding artifact from Math.ceil(), not a hack
            // (an actual FastBreak hack blows past this by hundreds of ms+,
            // repeatedly). 25ms (half a tick) was too tight to absorb that.
            if (diff < 60 || recentCustomBlock) {  // Reduce buffer if "close enough" (~1 tick), or a custom block's timing is untrustworthy
                blockBreakBalance *= 0.9;
            } else { // Otherwise, increase buffer
                blockBreakBalance += diff;
            }

            if (blockBreakBalance > 1000) { // If more than a second of advantage
                if (flagAndAlert("diff=" + diff + "ms, balance=" + blockBreakBalance + "ms, type=" + blockBreak.block.getType()) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }

            // Remember whether this break completed within a single client tick
            // (effectively instant, e.g. hardness-0 blocks) — vanilla allows
            // re-attempting such blocks at unlimited rate with no aiming delay,
            // which the START_DIGGING delay check above needs to know about.
            lastBreakWasInstant = maximumBlockDamage >= 1;

            // also set start time because the breaking netcode is fucked on 1.14.4+
            lastFinishBreak = startBreak = System.currentTimeMillis();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Find the most optimal block damage using the animation packet, which is sent at least once a tick when breaking blocks
        // On 1.8 clients, via screws with this packet meaning we must fall back to the 1.8 idle flying packet
        //
        // Backported from upstream Grim (2.0 branch, "fix fastbreak using outdated
        // onGround status" #2657, 2026-06-21): also listen on flying/movement
        // packets, since some block breaks can complete before the next ANIMATION
        // packet, leaving maximumBlockDamage computed against a stale onGround
        // status (BlockBreakSpeed halves damage off-ground) — directly the kind
        // of "predicted slower than real" gap behind our FastBreak false reports.
        boolean flying = WrapperPlayClientPlayerFlying.isFlying(event.getPacketType());
        if ((flying || (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) && event.getPacketType() == PacketType.Play.Client.ANIMATION)) && targetBlockPosition != null) {
            maximumBlockDamage = Math.max(maximumBlockDamage, BlockBreakSpeed.getBlockDamage(player, player.compensatedWorld.getBlock(targetBlockPosition)));
        }
    }

    private void clampBalance() {
        double balance = Math.max(1000, (player.getTransactionPing()));
        blockBreakBalance = GrimMath.clamp(blockBreakBalance, -balance, balance); // Clamp not Math.max in case other logic changes
        blockDelayBalance = GrimMath.clamp(blockDelayBalance, -balance, balance);
    }
}
