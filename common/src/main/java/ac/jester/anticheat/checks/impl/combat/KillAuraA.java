package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "KillAura", configName = "KillAuraA",
        description = "Attacking entity without swinging arm (no ANIMATION after INTERACT_ENTITY)")
public final class KillAuraA extends Check implements PacketCheck {
    private boolean pendingSwing = false;
    private int consecutiveNoSwing = 0;
    private static final int MIN_CONSECUTIVE = 3;

    public KillAuraA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Client.ANIMATION) {
            if (pendingSwing) consecutiveNoSwing = 0;
            pendingSwing = false;
            return;
        }

        if (isTickPacketIncludingNonMovement(type)) {
            if (pendingSwing) {
                pendingSwing = false;
                consecutiveNoSwing++;
                if (consecutiveNoSwing >= MIN_CONSECUTIVE
                        && player.getTransactionPing() < 600
                        && player.isTickingReliablyFor(5)) {
                    flagAndAlert(String.format("consecutive=%d ping=%dms",
                            consecutiveNoSwing, player.getTransactionPing()));
                    consecutiveNoSwing = 0;
                }
            }
            return;
        }

        if (type != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        pendingSwing = true;
    }
}
