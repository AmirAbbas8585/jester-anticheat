package ac.jester.anticheat.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.checks.type.RotationCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.RotationUpdate;
import ac.jester.anticheat.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * Aim A — aimbot detection via mouse-step quantization (GCD analysis).
 *
 * A real mouse rotates the camera in multiples of a fixed step derived from
 * the player's sensitivity: deltaYaw = step * pixels. Grim's AimProcessor
 * recovers that step as the GCD of consecutive rotation deltas. Aimbots
 * compute rotations mathematically (atan2 toward the target), producing
 * arbitrary floats with no common divisor — the GCD collapses to ~0.
 *
 * Scope: only significant yaw movements (1°-30°) while in combat (attacked an
 * entity within the last combatWindowTicks). Cinematic camera (F8) smoothing
 * also breaks quantization, but using it mid-PvP is implausible, and the high
 * consecutive requirement absorbs stray smoothed samples.
 */
@CheckData(name = "AimA", configName = "AimA",
        description = "Combat rotations not quantized by mouse sensitivity (aimbot)")
public final class AimA extends Check implements RotationCheck, PacketCheck {

    private int minConsecutive = 15;
    private int combatWindowTicks = 40;

    private int consecutiveUnquantized = 0;
    private int ticksSinceAttack = Integer.MAX_VALUE;

    public AimA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = config.getIntElse("AimA.min-consecutive", 15);
        combatWindowTicks = config.getIntElse("AimA.combat-window-ticks", 40);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            if (ticksSinceAttack != Integer.MAX_VALUE) ticksSinceAttack++;
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        ticksSinceAttack = 0;
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        // Only judge aim during combat — that's when aimbots rotate
        if (ticksSinceAttack > combatWindowTicks) {
            consecutiveUnquantized = 0;
            return;
        }

        float deltaYaw = rotationUpdate.getDeltaXRotABS();
        float pitch = Math.abs(rotationUpdate.getTo().pitch());

        // Tiny deltas drown in float noise; above ~5° float precision degrades
        // the GCD itself (grim's own AimProcessor only samples < 5°). Pitch
        // clamped at ±90 breaks the math too.
        if (deltaYaw < 1.0f || deltaYaw > 5.0f || pitch >= 89.5f) return;

        if (rotationUpdate.getProcessor().divisorX < GrimMath.MINIMUM_DIVISOR) {
            consecutiveUnquantized++;
            if (consecutiveUnquantized >= minConsecutive && player.isTickingReliablyFor(5)) {
                flagAndAlert(String.format("unquantized=%d deltaYaw=%.2f ping=%dms",
                        consecutiveUnquantized, deltaYaw, player.getTransactionPing()));
                consecutiveUnquantized = 0;
            }
        } else {
            consecutiveUnquantized = 0;
        }
    }
}
