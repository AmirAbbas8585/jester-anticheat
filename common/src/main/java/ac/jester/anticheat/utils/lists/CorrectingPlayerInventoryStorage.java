package ac.jester.anticheat.utils.lists;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.inventory.Inventory;
import ac.jester.anticheat.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.protocol.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CorrectingPlayerInventoryStorage extends InventoryStorage {
    private static final Set<String> SUPPORTED_INVENTORIES = new HashSet<>(
            Arrays.asList("CHEST", "DISPENSER", "DROPPER", "PLAYER", "ENDER_CHEST", "SHULKER_BOX", "BARREL", "CRAFTING", "CREATIVE")
    );
    private final GrimPlayer player;
    private final Map<Integer, Integer> serverIsCurrentlyProcessingThesePredictions = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingFinalizedSlot = new ConcurrentHashMap<>();

    public CorrectingPlayerInventoryStorage(GrimPlayer player, int size) {
        super(size);
        this.player = player;
    }

    public void handleClientClaimedSlotSet(int slotID) {
        if (slotID >= 0 && slotID <= Inventory.ITEMS_END) {
            pendingFinalizedSlot.put(slotID, GrimAPI.INSTANCE.getTickManager().currentTick + 5);
        }
    }

    public void handleServerCorrectSlot(int slotID) {
        if (slotID >= 0 && slotID <= Inventory.ITEMS_END) {
            serverIsCurrentlyProcessingThesePredictions.put(slotID, player.lastTransactionSent.get());
        }
    }

    @Override
    public void setItem(int item, ItemStack stack) {
        int finalTransaction = serverIsCurrentlyProcessingThesePredictions.getOrDefault(item, -1);

        if (finalTransaction == -1 || player.lastTransactionReceived.get() >= finalTransaction) {
            pendingFinalizedSlot.put(item, GrimAPI.INSTANCE.getTickManager().currentTick + 5);
            serverIsCurrentlyProcessingThesePredictions.remove(item);
        }

        super.setItem(item, stack);
    }

    private void checkThatBukkitIsSynced(int slot) {
        if (player.platformPlayer == null) return;
        if (!player.inventory.isPacketInventoryActive) return;

        int bukkitSlot = player.inventory.getBukkitSlot(slot);

        if (bukkitSlot != -1) {
            ItemStack existing = getItem(slot);
            ItemStack toPE = player.platformPlayer.getInventory().getStack(bukkitSlot, slot);

            if (existing.getType() != toPE.getType() || existing.getAmount() != toPE.getAmount()) {
                GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player.platformPlayer, GrimAPI.INSTANCE.getGrimPlugin(),
                        () -> player.platformPlayer.updateInventory(), null, 0);
                setItem(slot, toPE);
            }
        }
    }

    public void tickWithBukkit() {
        if (player.platformPlayer == null) return;

        int tickID = GrimAPI.INSTANCE.getTickManager().currentTick;
        for (Iterator<Map.Entry<Integer, Integer>> it = pendingFinalizedSlot.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> entry = it.next();
            if (entry.getValue() <= tickID) {
                checkThatBukkitIsSynced(entry.getKey());
                it.remove();
            }
        }

        if (player.inventory.needResend) {
            GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player.platformPlayer, GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                if (!player.inventory.needResend) return;

                if (SUPPORTED_INVENTORIES.contains(player.platformPlayer.getInventory().getOpenInventoryKey().toUpperCase(Locale.ROOT))) {
                    player.inventory.needResend = false;
                    player.platformPlayer.updateInventory();
                }
            }, null, 0);
        }

        if (tickID % 5 == 0) {
            int slotToCheck = (tickID / 5) % getSize();
            if (!pendingFinalizedSlot.containsKey(slotToCheck) && !serverIsCurrentlyProcessingThesePredictions.containsKey(slotToCheck)) {
                checkThatBukkitIsSynced(slotToCheck);
            }
        }
    }
}
