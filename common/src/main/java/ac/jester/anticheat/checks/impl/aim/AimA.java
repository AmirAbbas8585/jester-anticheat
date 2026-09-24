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
        if (ticksSinceAttack > combatWindowTicks) {
            consecutiveUnquantized = 0;
            return;
        }

        float deltaYaw = rotationUpdate.getDeltaXRotABS();
        float pitch = Math.abs(rotationUpdate.getTo().pitch());

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
