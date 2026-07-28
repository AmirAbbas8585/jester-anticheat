package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * AttributeSwap — detects automated abuse of the attribute-swap exploit (MC-28289).
 *
 * The exploit: switching hotbar slots in the same tick as an attack makes the
 * server read the damage from the NEW item while the cooldown that was already
 * charged belongs to the OLD one. Swapping sword -> mace -> sword around every
 * hit therefore lands full mace damage (Breach/Density included) at sword attack
 * speed. Doing it by hand is possible but wildly inconsistent; cheat modules
 * automate the whole sequence inside a single tick, every time.
 *
 * The signature we key on is the FULL round trip, not just a switch:
 *   slot A -> slot B -> ATTACK -> back to slot A
 * all inside the same client tick (or the one right after, since a tick boundary
 * can land mid-sequence). The swap-BACK is what separates this from ordinary
 * play: a player who genuinely changes weapon stays on the new weapon, they do
 * not return to the previous slot in the same breath. It also separates this
 * check from AutoWeapon, which flags switch+attack without a return.
 *
 * False-positive protections (this check kicks, so these matter):
 *   - The round trip must complete inside maxReturnTicks (default 1 tick).
 *   - minConsecutive (default 4) complete round trips IN A ROW are required.
 *     A single lucky scroll-click-scroll never flags.
 *   - ANY attack that is not part of a round trip resets the streak, which
 *     normal combat produces constantly.
 *   - Only counts while the player is ticking reliably, so a lag/ping burst that
 *     bunches packets from several ticks together cannot fabricate a sequence.
 * On top of that the global minimum-tps and high-ping punish guards still apply.
 */
@CheckData(name = "AttributeSwap", description = "Hotbar swap around an attack to stack weapon damage onto a shorter cooldown (MC-28289)")
public final class AttributeSwap extends Check implements PacketCheck {

    private int minConsecutive = 4;
    private int maxReturnTicks = 1;

    // Slot the player sat on before the current swap sequence started.
    private int slotBeforeSwap = -1;
    // Slot we are on right now, as far as the client has told us.
    private int currentSlot = -1;
    // Set when a switch happens with no attack yet — the "-> slot B" step.
    private boolean swappedBeforeAttack = false;
    // Tick the attack of the current sequence landed on, -1 when idle.
    private int attackTick = -1;
    // Slot held at the moment of that attack.
    private int slotAtAttack = -1;

    private int tick = 0;
    private int consecutive = 0;

    public AttributeSwap(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = Math.max(1, config.getIntElse("AttributeSwap.min-consecutive", 4));
        maxReturnTicks = Math.max(0, config.getIntElse("AttributeSwap.max-return-ticks", 1));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            tick++;
            // A sequence that never completed its return is not an attribute
            // swap — drop it once the window has passed.
            if (attackTick >= 0 && tick - attackTick > maxReturnTicks) {
                resetSequence();
                consecutive = 0;
            }
            if (attackTick < 0) swappedBeforeAttack = false;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            handleSlotChange(new WrapperPlayClientHeldItemChange(event).getSlot());
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (swappedBeforeAttack) {
            // slot A -> slot B -> ATTACK. Now waiting for the return to A.
            attackTick = tick;
            slotAtAttack = currentSlot;
        } else {
            // A normal attack with no swap in front of it — the streak is broken.
            resetSequence();
            consecutive = 0;
        }
    }

    private void handleSlotChange(int slot) {
        int previous = currentSlot;
        currentSlot = slot;
        if (previous == -1) return; // first slot we have seen, nothing to compare

        if (attackTick >= 0) {
            // We are waiting for the return leg of a sequence.
            boolean inTime = tick - attackTick <= maxReturnTicks;
            if (inTime && slot == slotBeforeSwap && slotAtAttack != slotBeforeSwap) {
                // Full round trip: A -> B -> attack -> A.
                consecutive++;
                resetSequence();
                if (consecutive >= minConsecutive && player.isTickingReliablyFor(3)) {
                    flagAndAlert(String.format(
                            "swap %d->%d->attack->%d in <=%d tick(s), consecutive=%d ping=%dms",
                            slotBeforeSwap, slotAtAttack, slot, maxReturnTicks,
                            consecutive, player.getTransactionPing()));
                    consecutive = 0;
                }
                // The slot we returned to becomes the base for the next sequence.
                slotBeforeSwap = slot;
                swappedBeforeAttack = false;
                return;
            }
            // Some other switch — this was not a clean round trip.
            resetSequence();
            consecutive = 0;
        }

        // Start of a possible new sequence: remember where we came from.
        slotBeforeSwap = previous;
        swappedBeforeAttack = true;
    }

    private void resetSequence() {
        attackTick = -1;
        slotAtAttack = -1;
        swappedBeforeAttack = false;
    }
}
