package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * PacketMine — detects instant block breaking (START_DIGGING + FINISHED_DIGGING
 * for the same block within a single tick).
 *
 * In survival mode, every block requires a minimum mining time. The client
 * sends START_DIGGING when the player begins holding left-click on a block,
 * then sends FINISHED_DIGGING only after the client's calculated mine time
 * has elapsed. The fastest legitimate non-instant break is 1 tick = 50ms,
 * so both packets arriving within a sub-tick window (< 30ms) means the
 * client skipped the mining progress entirely.
 *
 * This check does NOT fire in Creative mode where instant-break is vanilla.
 *
 * False-positive mitigations:
 *  - Sub-tick threshold (30ms): legit 1-tick breaks arrive ~50ms apart.
 *  - Consecutive requirement (default 3): TCP can bunch two packets sent
 *    50ms apart into one burst during a jitter spike — a one-off instant
 *    pair is ignored; a cheater mines many blocks and trips the streak.
 *
 * Note: Grim's FastBreak checks mine speed based on block hardness, but
 * PacketMine is distinct: it catches the case where the client omits the
 * mining duration entirely and sends both start and finish simultaneously.
 */
@CheckData(name = "PacketMine", description = "Sending mine-start and mine-finish in the same tick")
public final class PacketMine extends Check implements BlockBreakCheck {

    private long lastStartTime = 0L;
    private Vector3i lastStartPos = null;
    private int consecutiveInstant = 0;

    private int maxInstantMs = 30;
    private int minConsecutive = 3;

    public PacketMine(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxInstantMs = config.getIntElse("PacketMine.max-instant-ms", 30);
        minConsecutive = config.getIntElse("PacketMine.min-consecutive", 3);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        // Creative instant-break is vanilla behavior
        if (player.gamemode == GameMode.CREATIVE) return;
        if (!player.isTickingReliablyFor(3)) return;

        Vector3i pos = blockBreak.position;
        long now = System.currentTimeMillis();

        if (blockBreak.action == DiggingAction.START_DIGGING) {
            lastStartTime = now;
            lastStartPos = pos;
        } else if (blockBreak.action == DiggingAction.FINISHED_DIGGING) {
            if (lastStartPos != null && lastStartPos.equals(pos)) {
                long elapsed = now - lastStartTime;

                if (elapsed < maxInstantMs && player.getTransactionPing() < 300) {
                    consecutiveInstant++;
                    if (consecutiveInstant >= minConsecutive) {
                        flagAndAlert(String.format("start->finish=%dms max=%dms consecutive=%d pos=[%d,%d,%d] ping=%dms",
                                elapsed, maxInstantMs, consecutiveInstant,
                                pos.getX(), pos.getY(), pos.getZ(),
                                player.getTransactionPing()));
                        consecutiveInstant = 0;
                    }
                } else {
                    // Normal-speed break: streak broken
                    consecutiveInstant = 0;
                }
            }
            lastStartPos = null;
            lastStartTime = 0L;
        }
    }
}
