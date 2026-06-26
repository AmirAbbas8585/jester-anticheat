package ac.jester.anticheat.manager;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * Tracks the vanilla 1.9+ attack cooldown progress.
 *
 * In vanilla, attacking resets the attack cooldown. The "strength" of an attack
 * (and whether it causes sprint-slowdown) depends on how full the cooldown is
 * (0 = just swung, 1 = fully charged).
 *
 * Ported from Grim 2.3.74. Used by PacketPlayerAttack to accurately gate
 * whether a 1.9+ sprint-attack should apply the slowdown effect.
 */
@CheckData(name = "AttackCooldownHandler")
public final class AttackCooldownHandler extends Check implements PacketCheck {

    private int ticksSinceLastSwing = 0;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean stackChanged = false;

    public AttackCooldownHandler(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            reset();
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            // CANCELLED_DIGGING with face DOWN = release-block, which also resets the swing cooldown
            if (dig.getAction() == DiggingAction.CANCELLED_DIGGING && dig.getBlockFace() == BlockFace.DOWN) {
                reset();
            }
            return;
        }

        if (isTickPacket(event.getPacketType())) {
            if (!stackChanged) {
                ticksSinceLastSwing++;
            }
            updateHeldItem();
            stackChanged = false;
        }
    }

    /** Called when the player swings — resets the cooldown. */
    public void reset() {
        ticksSinceLastSwing = 0;
    }

    /**
     * Returns cooldown progress in [0, 1] mirroring vanilla's getAttackStrengthScale(0.5).
     * 0 = just swung (cooldown empty), 1 = fully charged.
     */
    public float getMinimumProgress() {
        double attackSpeed = player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed <= 0) return 1f;
        float ticks = (float) (1.0 / attackSpeed * 20.0);
        return GrimMath.clamp((ticksSinceLastSwing + 0.5f) / ticks, 0f, 1f);
    }

    private void updateHeldItem() {
        ItemStack current = player.inventory.getHeldItem();
        if (current == null) current = ItemStack.EMPTY;

        boolean changed;
        if (stack.isEmpty() && current.isEmpty()) {
            changed = false;
        } else if (stack.isEmpty() != current.isEmpty()) {
            changed = true;
        } else {
            changed = stack.getType() != current.getType()
                    || (!current.isDamageableItem() && stack.getLegacyData() != current.getLegacyData());
        }

        if (changed) {
            reset();
            stackChanged = true;
        }
        stack = current.copy();
    }
}
