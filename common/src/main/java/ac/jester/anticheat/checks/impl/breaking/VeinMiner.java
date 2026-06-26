package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.util.Vector3i;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;

import java.util.ArrayDeque;

/**
 * VeinMiner — detects mining multiple distinct blocks in rapid succession.
 *
 * VeinMiner/ChainMine hacks automatically break chains of connected blocks
 * (ores, logs, etc.) far faster than a human could manually click each one.
 *
 * Detection: If the player finishes breaking more than maxBps distinct block
 * positions per second, it's VeinMiner. Normal mining is physically limited
 * to 1 block per several ticks depending on tool/block type.
 *
 * IMPORTANT: only FINISHED_DIGGING counts. START_DIGGING fires on every
 * left-click (even one that breaks nothing), and instant-break blocks
 * (grass, torches, ...) only send START — counting those false flagged
 * normal players for clicking 4 times. Blocks that take time to break
 * always send FINISHED, which is what VeinMiner hacks produce per block.
 *
 * FastBreak already covers breaking blocks faster than hardness allows.
 * This check specifically targets the multi-block aspect: breaking many
 * DIFFERENT positions in quick succession.
 */
@CheckData(name = "VeinMiner", description = "Breaking many distinct block positions per second")
public final class VeinMiner extends Check implements BlockBreakCheck {

    private int maxBps = 5;
    private final ArrayDeque<Long> breakTimestamps = new ArrayDeque<>();
    private Vector3i lastCountedPos = null;

    public VeinMiner(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        // Only completed breaks — clicks (START) and aborts (CANCELLED) are not breaks
        if (blockBreak.action != DiggingAction.FINISHED_DIGGING) return;
        if (player.gamemode == GameMode.CREATIVE) return;
        if (!player.isTickingReliablyFor(3)) return;

        // Distinct positions only — re-break packets for the same block don't count
        if (blockBreak.position.equals(lastCountedPos)) return;
        lastCountedPos = blockBreak.position;

        long now = System.currentTimeMillis();
        breakTimestamps.addLast(now);

        while (!breakTimestamps.isEmpty() && now - breakTimestamps.peekFirst() > 1000L) {
            breakTimestamps.pollFirst();
        }

        int bps = breakTimestamps.size();
        if (bps > maxBps) {
            Vector3i pos = blockBreak.position;
            if (flagAndAlert(String.format("bps=%d max=%d pos=%d,%d,%d",
                    bps, maxBps, pos.getX(), pos.getY(), pos.getZ()))) {
                breakTimestamps.clear();
            }
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBps = config.getIntElse("VeinMiner.max-bps", 5);
    }
}
