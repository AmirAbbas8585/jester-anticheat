package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * KillAura A — No-swing attack detection.
 *
 * Vanilla packet order within one client tick when attacking:
 *   1. INTERACT_ENTITY (attack)   ← sent FIRST
 *   2. ANIMATION (arm swing)      ← sent immediately AFTER, same tick
 *   3. movement packet            ← end of tick
 *
 * So after an attack, the swing MUST arrive before the end-of-tick movement
 * packet. KillAura cheats that skip the swing leave the attack "unswung" at
 * the tick boundary.
 *
 * (The old implementation expected the swing BEFORE the attack — backwards —
 * which false flagged the first attack after every pause.)
 *
 * Consecutive requirement: packet reordering through proxies/Via is rare but
 * possible, so a single no-swing attack is ignored; cheats produce them on
 * every attack.
 */
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
            // Swing arrived — the pending attack was legit
            if (pendingSwing) consecutiveNoSwing = 0;
            pendingSwing = false;
            return;
        }

        // End of client tick: an attack without a swing in the same tick = no-swing
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
