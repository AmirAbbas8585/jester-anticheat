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
                    consecutiveInstant = 0;
                }
            }
            lastStartPos = null;
            lastStartTime = 0L;
        }
    }
}
