package ac.jester.anticheat.checks.impl.breaking;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

@CheckData(name = "Nuker", configName = "NukerA",
        description = "Sending multiple block-break start packets simultaneously (Nuker/MultiBreak)")
public final class NukerA extends Check implements PacketCheck {
    private final Set<Long> diggingPositionsThisTick = new HashSet<>();

    private final ArrayDeque<Long> finishedBreaks = new ArrayDeque<>();
    private int windowSeconds = 3;
    private int maxBreaksPerWindow = 15;
    private int maxSimultaneous = 3;

    private long lastFinishedPosHash = Long.MIN_VALUE;

    public NukerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxBreaksPerWindow = config.getIntElse(getConfigName() + ".max-breaks-per-window", 15);
        windowSeconds = Math.max(1, config.getIntElse(getConfigName() + ".window-seconds", 3));
        maxSimultaneous = Math.max(2, config.getIntElse(getConfigName() + ".max-simultaneous", 3));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (isTickPacketIncludingNonMovement(type)) {
            diggingPositionsThisTick.clear();
            return;
        }

        if (type != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = digging.getAction();

        if (action == DiggingAction.START_DIGGING) {
            Vector3i pos = digging.getBlockPosition();
            long posHash = ((long) pos.getX() & 0x3FFFFFF) | (((long) pos.getZ() & 0x3FFFFFF) << 26) | ((long) pos.getY() << 52);
            diggingPositionsThisTick.add(posHash);

            if (hasPerTickMarker() && diggingPositionsThisTick.size() >= maxSimultaneous) {
                flagAndAlert(String.format("simultaneous_starts=%d in one tick", diggingPositionsThisTick.size()));
                diggingPositionsThisTick.clear();
            }
        } else if (action == DiggingAction.FINISHED_DIGGING) {
            long now = System.currentTimeMillis();

            Vector3i finishedPos = digging.getBlockPosition();
            long finishedPosHash = ((long) finishedPos.getX() & 0x3FFFFFF) | (((long) finishedPos.getZ() & 0x3FFFFFF) << 26) | ((long) finishedPos.getY() << 52);
            boolean samePositionAsLast = finishedPosHash == lastFinishedPosHash;
            lastFinishedPosHash = finishedPosHash;

            if (samePositionAsLast) return;

            finishedBreaks.addLast(now);

            long cutoff = now - (windowSeconds * 1000L);
            while (!finishedBreaks.isEmpty() && finishedBreaks.peekFirst() < cutoff) {
                finishedBreaks.pollFirst();
            }

            if (player.gamemode == com.github.retrooper.packetevents.protocol.player.GameMode.CREATIVE) return;

            if (finishedBreaks.size() > maxBreaksPerWindow) {
                double rate = (double) finishedBreaks.size() / windowSeconds;
                flagAndAlert(String.format("rate=%.1f/s in %ds window", rate, windowSeconds));
                finishedBreaks.clear();
            }
        }
    }
}
