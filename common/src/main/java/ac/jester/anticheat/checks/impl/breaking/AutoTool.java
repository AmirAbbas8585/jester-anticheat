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
            consecutiveFastSwitches = 0;
        }
    }
}
