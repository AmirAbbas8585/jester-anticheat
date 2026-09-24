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

@CheckData(name = "Nuker", configName = "NukerB",
        description = "Breaking blocks in multiple directions rapidly (Nuker pattern)")
public final class NukerB extends Check implements BlockBreakCheck {
    private static final long SPAN_WINDOW_MS = 500L;
    private double maxSpanDegrees = 120.0;
    private int maxCreativeBps = 8;

    private float firstBreakYaw = Float.NaN;
    private long windowStart = 0L;
    private int breakCount = 0;
    private double maxYawSeenInWindow = 0;

    private long creativeBurstStart = 0L;
    private int creativeBurstCount = 0;

    public NukerB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxSpanDegrees = config.getDoubleElse("NukerB.max-span-degrees", 120.0);
        maxCreativeBps = config.getIntElse("NukerB.max-creative-bps", 8);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.FINISHED_DIGGING) return;
        if (!player.isTickingReliablyFor(3)) return;

        long now = System.currentTimeMillis();
        Vector3i pos = blockBreak.position;

        if (player.gamemode == GameMode.CREATIVE) {
            if (now - creativeBurstStart > 1000L) {
                creativeBurstStart = now;
                creativeBurstCount = 0;
            }
            creativeBurstCount++;
            if (creativeBurstCount > maxCreativeBps) {
                flagAndAlert(String.format("creative_bps=%d max=%d", creativeBurstCount, maxCreativeBps));
                creativeBurstCount = 0;
            }
            return;
        }

        double dx = pos.getX() + 0.5 - player.x;
        double dz = pos.getZ() + 0.5 - player.z;
        if (dx * dx + dz * dz < 1.5 * 1.5) return;
        float breakYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        if (now - windowStart > SPAN_WINDOW_MS || Float.isNaN(firstBreakYaw)) {
            windowStart = now;
            firstBreakYaw = breakYaw;
            maxYawSeenInWindow = 0;
            breakCount = 1;
            return;
        }

        breakCount++;

        double yawDiff = breakYaw - firstBreakYaw;
        yawDiff = ((yawDiff % 360) + 540) % 360 - 180;
        double absYawDiff = Math.abs(yawDiff);
        maxYawSeenInWindow = Math.max(maxYawSeenInWindow, absYawDiff);

        if (breakCount >= 3 && maxYawSeenInWindow > maxSpanDegrees) {
            flagAndAlert(String.format("span=%.1f° max=%.0f° breaks=%d in %dms",
                    maxYawSeenInWindow, maxSpanDegrees, breakCount,
                    now - windowStart));
            firstBreakYaw = breakYaw;
            windowStart = now;
            breakCount = 1;
            maxYawSeenInWindow = 0;
        }
    }
}
