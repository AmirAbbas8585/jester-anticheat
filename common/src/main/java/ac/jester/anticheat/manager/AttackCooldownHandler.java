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

    public void reset() {
        ticksSinceLastSwing = 0;
    }

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
