package ac.jester.anticheat.utils.inventory.slot;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.protocol.item.ItemStack;

public class ResultSlot extends Slot {

    public ResultSlot(InventoryStorage container, int slot) {
        super(container, slot);
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public void onTake(GrimPlayer player, ItemStack itemStack) {
        // Resync the player's inventory
    }
}
