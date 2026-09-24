package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "AutoClicker", configName = "AutoClickerB",
        description = "Multiple swing packets sent within a single client tick")
public final class AutoClickerB extends Check implements PacketCheck {
    private int animationsThisTick = 0;
    private boolean useSwingPending = false;
    private boolean digging = false;

    private int recentBursts = 0;
    private long lastBurstTime = 0L;
    private long burstWindowMs = 4000L;
    private int minBursts = 4;

    private boolean cancelBeforeKick = true;
    private long cancelDurationMs = 3000L;
    private long cancelUntil = 0L;

    public AutoClickerB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minBursts = config.getIntElse("AutoClickerB.min-bursts", 4);
        burstWindowMs = config.getIntElse("AutoClickerB.burst-window-ms", 4000);
        cancelBeforeKick = config.getBooleanElse("AutoClickerB.cancel-before-kick", true);
        cancelDurationMs = config.getIntElse("AutoClickerB.cancel-duration-ms", 3000);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!hasPerTickMarker()) return;
        PacketTypeCommon type = event.getPacketType();

        if (cancelBeforeKick && type == PacketType.Play.Client.INTERACT_ENTITY
                && System.currentTimeMillis() < cancelUntil
                && shouldModifyPackets()) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return;
        }

        if (isTickPacketIncludingNonMovement(type)) {
            animationsThisTick = 0;
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

        if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging dig =
                    new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging(event);
            switch (dig.getAction()) {
                case START_DIGGING -> digging = true;
                case CANCELLED_DIGGING, FINISHED_DIGGING -> digging = false;
                default -> { }
            }
            return;
        }

        if (type != PacketType.Play.Client.ANIMATION) return;
        if (digging) return;
        if (useSwingPending) {
            useSwingPending = false;
            return;
        }

        animationsThisTick++;

        if (animationsThisTick >= 3 && player.isTickingReliablyFor(3)) {
            long now = System.currentTimeMillis();
            recentBursts = (now - lastBurstTime < burstWindowMs) ? recentBursts + 1 : 1;
            lastBurstTime = now;

            if (recentBursts >= minBursts) {
                if (flagAndAlert("anims=" + animationsThisTick + " in one tick ping=" + player.getTransactionPing() + "ms consecutive=" + recentBursts)
                        && cancelBeforeKick) {
                    cancelUntil = now + cancelDurationMs;
                }
                recentBursts = 0;
            }
            animationsThisTick = 0;
        }
    }
}
