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

@CheckData(name = "AttributeSwap", description = "Hotbar swap around an attack to stack weapon damage onto a shorter cooldown (MC-28289)")
public final class AttributeSwap extends Check implements PacketCheck {
    private int minConsecutive = 4;
    private int maxReturnTicks = 1;

    private int slotBeforeSwap = -1;
    private int currentSlot = -1;
    private boolean swappedBeforeAttack = false;
    private int attackTick = -1;
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
        if (!hasPerTickMarker()) return;
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            tick++;
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
            attackTick = tick;
            slotAtAttack = currentSlot;
        } else {
            resetSequence();
            consecutive = 0;
        }
    }

    private void handleSlotChange(int slot) {
        int previous = currentSlot;
        currentSlot = slot;
        if (previous == -1) return;

        if (attackTick >= 0) {
            boolean inTime = tick - attackTick <= maxReturnTicks;
            if (inTime && slot == slotBeforeSwap && slotAtAttack != slotBeforeSwap) {
                consecutive++;
                resetSequence();
                if (consecutive >= minConsecutive && player.isTickingReliablyFor(3)) {
                    flagAndAlert(String.format(
                            "swap %d->%d->attack->%d in <=%d tick(s), consecutive=%d ping=%dms",
                            slotBeforeSwap, slotAtAttack, slot, maxReturnTicks,
                            consecutive, player.getTransactionPing()));
                    consecutive = 0;
                }
                slotBeforeSwap = slot;
                swappedBeforeAttack = false;
                return;
            }
            resetSequence();
            consecutive = 0;
        }

        slotBeforeSwap = previous;
        swappedBeforeAttack = true;
    }

    private void resetSequence() {
        attackTick = -1;
        slotAtAttack = -1;
        swappedBeforeAttack = false;
    }
}
