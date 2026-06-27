package ac.jester.anticheat.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * Aim — rotation-quantization (GCD) aimbot detection.
 *
 * A human aims with a mouse: every yaw/pitch change the client sends is an
 * integer multiple of a tiny "sensitivity step" (mouse counts × sensitivity), so
 * across many rotations the deltas all share a common divisor — that step. An
 * aimbot computes target angles directly, so its deltas have no common divisor
 * and the greatest-common-divisor of the rotation deltas collapses toward the
 * rounding floor.
 *
 * We collect a rolling window of rotation deltas DURING COMBAT, expand them to
 * integers, and compute their GCD. A consistently tiny GCD is the signature of
 * computed (aimbot) aim. Self-contained — only rotation/attack packets, no
 * movement prediction.
 *
 * The GCD-of-rotation idea is a publicly known heuristic; this is an original
 * implementation. It is alert-only by default and the thresholds are meant to be
 * calibrated from real logs (rotation float precision makes a single universal
 * threshold imperfect).
 */
@CheckData(name = "Aim", configName = "AimGCD",
        description = "Combat rotations not quantized to a mouse sensitivity (aimbot)")
public final class AimGCD extends Check implements PacketCheck {

    // Degrees -> integer expander. Kept moderate so a real sensitivity step maps
    // above the float-rounding noise of the rotation deltas, not so large that
    // the noise itself survives and breaks a legitimate player's GCD.
    private double expander = 8192.0;
    private int windowSize = 40;
    private long minGcd = 40;            // GCD at/below this == "no common step"
    private int minConsecutive = 6;      // bad evaluations in a row before flagging
    private long combatWindowMs = 1500;  // only judge rotations shortly after an attack

    private long[] samples;
    private int idx = 0;
    private int count = 0;
    private float lastYaw = 0f, lastPitch = 0f;
    private boolean hasLast = false;
    private long lastAttack = 0L;
    private int consecutiveBad = 0;

    public AimGCD(GrimPlayer player) {
        super(player);
        samples = new long[windowSize];
    }

    @Override
    public void onReload(ConfigManager config) {
        expander = config.getDoubleElse("AimGCD.expander", 8192.0);
        windowSize = Math.max(10, config.getIntElse("AimGCD.window-size", 40));
        minGcd = config.getLongElse("AimGCD.min-gcd", 40);
        minConsecutive = Math.max(1, config.getIntElse("AimGCD.min-consecutive", 6));
        combatWindowMs = config.getLongElse("AimGCD.combat-window-ms", 1500);
        samples = new long[windowSize];
        idx = 0;
        count = 0;
        consecutiveBad = 0;
        hasLast = false;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                lastAttack = System.currentTimeMillis();
            }
            return;
        }

        if (!WrapperPlayClientPlayerFlying.isFlying(type)) return;
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        if (!flying.hasRotationChanged()) return;

        float yaw = flying.getLocation().getYaw();
        float pitch = flying.getLocation().getPitch();

        if (!hasLast) {
            lastYaw = yaw;
            lastPitch = pitch;
            hasLast = true;
            return;
        }

        float dYaw = Math.abs(wrapDegrees(yaw - lastYaw));
        float dPitch = Math.abs(pitch - lastPitch);
        lastYaw = yaw;
        lastPitch = pitch;

        addSample(dYaw);
        addSample(dPitch);

        if (count < windowSize) return;

        // Only judge during combat — that's where aimbot matters, and it removes
        // the noise of ordinary looking-around.
        if (System.currentTimeMillis() - lastAttack > combatWindowMs) {
            consecutiveBad = 0;
            return;
        }

        long g = windowGcd();
        if (g > 0 && g <= minGcd) {
            if (++consecutiveBad >= minConsecutive) {
                flagAndAlert("gcd=" + g + " min=" + minGcd + " window=" + windowSize
                        + " ping=" + player.getTransactionPing() + "ms");
                consecutiveBad = 0;
            }
        } else {
            consecutiveBad = 0;
        }
    }

    private void addSample(float deltaDeg) {
        // Skip non-rotations (noise) and teleport/flick-scale jumps that aren't a
        // clean sensitivity-step multiple.
        if (deltaDeg < 0.04f || deltaDeg > 30f) return;
        long v = Math.round((double) deltaDeg * expander);
        if (v <= 0) return;
        samples[idx] = v;
        idx = (idx + 1) % windowSize;
        if (count < windowSize) count++;
    }

    private long windowGcd() {
        long g = 0;
        for (int i = 0; i < count; i++) {
            g = gcd(g, samples[i]);
            if (g == 1) break; // can't get smaller
        }
        return g;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}
