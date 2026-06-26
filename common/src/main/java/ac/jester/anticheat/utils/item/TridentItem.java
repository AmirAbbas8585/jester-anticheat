package ac.jester.anticheat.utils.item;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class TridentItem extends ItemBehaviour {

    public static final TridentItem INSTANCE = new TridentItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, GrimPlayer player, InteractionHand hand) {
        if (this.nextDamageWillBreak(item)) {
            return false;
        }

        return item.getEnchantmentLevel(EnchantmentTypes.RIPTIDE) <= 0;
    }

    private boolean nextDamageWillBreak(ItemStack item) {
        return item.isDamageableItem() && item.getDamageValue() >= item.getMaxDamage() - 1;
    }

}
