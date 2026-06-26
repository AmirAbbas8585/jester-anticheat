package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockBreakCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * AutoTool — detects automated tool switching immediately before block breaks.
 *
 * AutoTool automatically selects the optimal tool for the block the player
 * is about to break, then switches back. The switch happens within a single
 * tick (< 50ms) every time — a timing that is impossible to maintain
 * consistently by hand for more than one or two accidental occurrences.
 *
 * Detection: When HELD_ITEM_CHANGE is followed by START_DIGGING within
 * singleSwitchMs, that's a "switch+dig" event. If this happens consecutively
 * minConsecutive or more times, it is flagged.
 *
 * The consecutive requirement significantly reduces false positives: a player
 * might occasionally switch quickly before a break, but AutoTool does it
 * every single time.
 *
 * Vanilla scroll-restart exclusion: scrolling the hotbar while holding
 * left-click makes the client re-send START_DIGGING for the SAME block
 * immediately after HELD_ITEM_CHANGE — 100% legit and very common while
 * strip-mining. We therefore only count switch→dig pairs where the dig
 * targets a NEW position (different block from the previous dig).
 */
@CheckData(name = "AutoTool", description = "Automatically switches to optimal tool before every break")
public final class AutoTool extends Check implements BlockBreakCheck {

    private long lastSwitchTime = 0L;
    private int consecutiveFastSwitches = 0;
    private Vector3i lastDigPos = null;

    private int singleSwitchMs = 50;
    private int minConsecutive = 3;

    public AutoTool(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        singleSwitchMs = config.getIntElse("AutoTool.switch-ms", 50);
        minConsecutive = config.getIntElse("AutoTool.min-consecutive", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            lastSwitchTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.START_DIGGING) return;
        if (!player.isTickingReliablyFor(3)) return;

        Vector3i pos = blockBreak.position;
        boolean newBlock = lastDigPos == null || !lastDigPos.equals(pos);
        lastDigPos = pos;

        // Re-digging the same block after a hotbar scroll is vanilla behavior —
        // ignore it entirely (neither counts nor resets the streak)
        if (!newBlock) {
            lastSwitchTime = 0;
            return;
        }

        long now = System.currentTimeMillis();

        if (lastSwitchTime > 0) {
            long elapsed = now - lastSwitchTime;
            if (elapsed < singleSwitchMs) {
                consecutiveFastSwitches++;
                if (consecutiveFastSwitches >= minConsecutive) {
                    flagAndAlert(String.format("switch->dig=%dms max=%dms consecutive=%d ping=%dms",
                            elapsed, singleSwitchMs, consecutiveFastSwitches,
                            player.getTransactionPing()));
                    consecutiveFastSwitches = 0;
                }
            } else {
                consecutiveFastSwitches = 0;
            }
            lastSwitchTime = 0;
        } else {
            // No switch before this dig: reset consecutive counter
            consecutiveFastSwitches = 0;
        }
    }
}
