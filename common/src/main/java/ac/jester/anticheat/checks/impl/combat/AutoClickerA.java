package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;

@CheckData(name = "AutoClicker", configName = "AutoClickerA",
        description = "Statistically consistent click intervals indicating AutoClicker")
public final class AutoClickerA extends Check implements PacketCheck {
    private int windowSize = 20;
    private double maxCpsHuman = 20.0;
    private double minCvHuman = 0.10;

    private boolean cancelBeforeKick = true;
    private long cancelDurationMs = 3000L;
    private long cancelUntil = 0L;
    private int consecutiveCpsOver = 0;
    private int minConsecutiveCps = 2;

    private ArrayDeque<Long> clickTimes = new ArrayDeque<>(windowSize + 1);
    private long lastClickTime = 0L;
    private volatile double lastCps = 0.0;

    private boolean digging = false;
    private boolean useSwingPending = false;
    private long lastDigPacketTime = 0L;
    private static final long DIG_GRACE_MS = 500L;

    public AutoClickerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        windowSize = Math.max(8, config.getIntElse("AutoClickerA.window-size", 20));
        maxCpsHuman = config.getDoubleElse("AutoClickerA.max-cps", 20.0);
        minCvHuman = config.getDoubleElse("AutoClickerA.min-cv", 0.10);
        cancelBeforeKick = config.getBooleanElse("AutoClickerA.cancel-before-kick", true);
        cancelDurationMs = config.getIntElse("AutoClickerA.cancel-duration-ms", 3000);
        minConsecutiveCps = config.getIntElse("AutoClickerA.min-consecutive-cps", 2);
        clickTimes = new ArrayDeque<>(windowSize + 1);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (cancelBeforeKick
                && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                && System.currentTimeMillis() < cancelUntil
                && shouldModifyPackets()) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return;
        }

        PacketTypeCommon type = event.getPacketType();
        if (isTickPacketIncludingNonMovement(type)) {
            useSwingPending = false;
            return;
        }
        if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            useSwingPending = true;
            return;
        }
        if (type == PacketType.Play.Client.INTERACT_ENTITY
                && new WrapperPlayClientInteractEntity(event).getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            useSwingPending = true;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging1 = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = digging1.getAction();
            if (action == DiggingAction.START_DIGGING) {
                digging = true;
                lastDigPacketTime = System.currentTimeMillis();
                clickTimes.clear();
                lastClickTime = 0L;
            } else if (action == DiggingAction.CANCELLED_DIGGING || action == DiggingAction.FINISHED_DIGGING) {
                digging = false;
                lastDigPacketTime = System.currentTimeMillis();
                clickTimes.clear();
                lastClickTime = 0L;
            }
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) return;
        if (useSwingPending) {
            useSwingPending = false;
            return;
        }

        long now = System.currentTimeMillis();

        if (digging || now - lastDigPacketTime < DIG_GRACE_MS) {
            clickTimes.clear();
            lastClickTime = 0L;
            return;
        }

        if (lastClickTime != 0L) {
            long interval = now - lastClickTime;
            if (interval > 2000L) {
                clickTimes.clear();
                lastClickTime = now;
                return;
            }

            clickTimes.addLast(interval);
            if (clickTimes.size() > windowSize) {
                clickTimes.pollFirst();
            }

            if (clickTimes.size() >= windowSize) {
                double mean = clickTimes.stream().mapToLong(Long::longValue).average().orElse(0);
                if (mean < 1) {
                    lastClickTime = now;
                    return;
                }

                double cps = 1000.0 / mean;
                lastCps = cps;

                if (cps > maxCpsHuman) {
                    consecutiveCpsOver++;
                    if (consecutiveCpsOver >= minConsecutiveCps
                            && flagAndAlert(String.format("cps=%.1f mean=%.1fms consecutive=%d", cps, mean, consecutiveCpsOver))
                            && cancelBeforeKick) {
                        cancelUntil = now + cancelDurationMs;
                    }
                    lastClickTime = now;
                    return;
                }
                consecutiveCpsOver = 0;

                double variance = clickTimes.stream()
                        .mapToDouble(t -> (t - mean) * (t - mean))
                        .average().orElse(0);
                double stddev = Math.sqrt(variance);
                double cv = stddev / mean;

                if (cv < minCvHuman && cps > 6.0
                        && flagAndAlert(String.format("cv=%.3f cps=%.1f mean=%.1fms", cv, cps, mean))
                        && cancelBeforeKick) {
                    cancelUntil = now + cancelDurationMs;
                }
            }
        }

        lastClickTime = now;
    }

    public double getLastCps() { return lastCps; }
}
