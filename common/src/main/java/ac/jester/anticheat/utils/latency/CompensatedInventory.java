package ac.jester.anticheat.utils.latency;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.inventory.EquipmentType;
import ac.jester.anticheat.utils.inventory.Inventory;
import ac.jester.anticheat.utils.inventory.inventory.AbstractContainerMenu;
import ac.jester.anticheat.utils.inventory.inventory.MenuType;
import ac.jester.anticheat.utils.inventory.inventory.NotImplementedMenu;
import ac.jester.anticheat.utils.lists.CorrectingPlayerInventoryStorage;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenHorseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompensatedInventory extends Check implements PacketCheck {
    private static final int PLAYER_INVENTORY_CASE = -1;
    private static final int UNSUPPORTED_INVENTORY_CASE = -2;
    public final Inventory inventory;
    public AbstractContainerMenu menu;
    public boolean isPacketInventoryActive = true;
    public boolean needResend = false;
    public int stateID = 0;
    private int openWindowID = 0;
    private int packetSendingInventorySize = PLAYER_INVENTORY_CASE;

    private ItemStack startOfTickStack = ItemStack.EMPTY;

    public ItemStack getStartOfTickStack() {
        return startOfTickStack;
    }

    public int getOpenWindowID() {
        return openWindowID;
    }

    public boolean hasExternalContainerOpen() {
        return openWindowID != 0;
    }

    public CompensatedInventory(GrimPlayer playerData) {
        super(playerData);

        CorrectingPlayerInventoryStorage storage = new CorrectingPlayerInventoryStorage(player, 46);
        inventory = new Inventory(playerData, storage);

        menu = inventory;
    }

    // Taken from https://www.spigotmc.org/threads/mapping-protocol-to-bukkit-slots.577724/
    public int getBukkitSlot(int packetSlot) {
        if (packetSlot <= 4) {
            return -1;
        }
        if (packetSlot <= 8) {
            return (7 - packetSlot) + 36;
        }
        if (packetSlot <= 35) {
            return packetSlot;
        }
        if (packetSlot <= 44) {
            return packetSlot - 36;
        }
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9) && packetSlot == 45) {
            return 40;
        }
        return -1;
    }

    private void markPlayerSlotAsChanged(int clicked) {
        if (openWindowID == 0) {
            inventory.getInventoryStorage().handleClientClaimedSlotSet(clicked);
            return;
        }

        if (menu instanceof NotImplementedMenu) return;

        int nonPlayerInvSize = menu.getSlots().size() - 36 + 9;
        int playerInvSlotclicked = clicked - nonPlayerInvSize;
        inventory.getInventoryStorage().handleClientClaimedSlotSet(playerInvSlotclicked);
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? getHeldItem() : getOffHand();
    }

    private void markServerForChangingSlot(int clicked, int windowID) {
        if (packetSendingInventorySize == UNSUPPORTED_INVENTORY_CASE) return;
        if (packetSendingInventorySize == PLAYER_INVENTORY_CASE || windowID == 0) {
            inventory.getInventoryStorage().handleServerCorrectSlot(clicked);
            return;
        }
        int nonPlayerInvSize = menu.getSlots().size() - 36 + 9;
        int playerInvSlotclicked = clicked - nonPlayerInvSize;

        inventory.getInventoryStorage().handleServerCorrectSlot(playerInvSlotclicked);
    }

    public ItemStack getHeldItem() {
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getHeldItem() :
                player.platformPlayer.getInventory().getItemInHand();
        return item == null ? ItemStack.EMPTY : item;
    }

    public ItemStack getOffHand() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9))
            return ItemStack.EMPTY;
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getOffhand() :
                player.platformPlayer.getInventory().getItemInOffHand();
        return item == null ? ItemStack.EMPTY : item;
    }

    public ItemStack getHelmet() {
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getHelmet() :
                player.platformPlayer.getInventory().getHelmet();
        return item == null ? ItemStack.EMPTY : item;
    }

    public ItemStack getChestplate() {
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getChestplate() :
                player.platformPlayer.getInventory().getChestplate();
        return item == null ? ItemStack.EMPTY : item;
    }

    public ItemStack getLeggings() {
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getLeggings() :
                player.platformPlayer.getInventory().getLeggings();
        return item == null ? ItemStack.EMPTY : item;
    }

    public ItemStack getBoots() {
        ItemStack item = isPacketInventoryActive || player.platformPlayer == null ? inventory.getBoots() :
                player.platformPlayer.getInventory().getBoots();
        return item == null ? ItemStack.EMPTY : item;
    }

    private ItemStack getByEquipmentType(EquipmentType type) {
        return switch (type) {
            case HEAD -> getHelmet();
            case CHEST -> getChestplate();
            case LEGS -> getLeggings();
            case FEET -> getBoots();
            case OFFHAND -> getOffHand();
            case MAINHAND -> getHeldItem();
        };
    }

    public boolean hasItemType(ItemType type) {
        if (isPacketInventoryActive || player.platformPlayer == null)
            return inventory.hasItemType(type);

        for (ItemStack itemStack : player.platformPlayer.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() == type) return true;
        }
        return false;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            WrapperPlayClientUseItem item = new WrapperPlayClientUseItem(event);

            ItemStack use = item.getHand() == InteractionHand.MAIN_HAND ? getHeldItem() : getOffHand();

            EquipmentType equipmentType = EquipmentType.getEquipmentSlotForItem(use);
            if (equipmentType != null) {
                int slot;
                switch (equipmentType) {
                    case HEAD -> slot = Inventory.SLOT_HELMET;
                    case CHEST -> slot = Inventory.SLOT_CHESTPLATE;
                    case LEGS -> slot = Inventory.SLOT_LEGGINGS;
                    case FEET -> slot = Inventory.SLOT_BOOTS;
                    default -> {
                        return;
                    }
                }

                ItemStack currentEquippedItem = getByEquipmentType(equipmentType);
                if (player.getClientVersion().isOlderThan(ClientVersion.V_1_19_4) && !currentEquippedItem.isEmpty())
                    return;

                int swapItemSlot = item.getHand() == InteractionHand.MAIN_HAND ? inventory.selected + Inventory.HOTBAR_OFFSET : Inventory.SLOT_OFFHAND;

                inventory.getInventoryStorage().handleClientClaimedSlotSet(swapItemSlot);
                inventory.getInventoryStorage().setItem(swapItemSlot, currentEquippedItem);

                inventory.getInventoryStorage().handleClientClaimedSlotSet(slot);
                inventory.getInventoryStorage().setItem(slot, use);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);

            if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) return;

            if (dig.getAction() == DiggingAction.DROP_ITEM) {
                ItemStack heldItem = getHeldItem();
                if (heldItem != null) {
                    heldItem.setAmount(heldItem.getAmount() - 1);
                    if (heldItem.getAmount() <= 0) {
                        heldItem = null;
                    }
                }
                inventory.setHeldItem(heldItem);
                inventory.getInventoryStorage().handleClientClaimedSlotSet(Inventory.HOTBAR_OFFSET + player.packetStateData.lastSlotSelected);
            }

            if (dig.getAction() == DiggingAction.DROP_ITEM_STACK) {
                inventory.setHeldItem(null);
                inventory.getInventoryStorage().handleClientClaimedSlotSet(Inventory.HOTBAR_OFFSET + player.packetStateData.lastSlotSelected);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            final int slot = new WrapperPlayClientHeldItemChange(event).getSlot();

            if (slot > 8 || slot < 0) return;

            inventory.selected = slot;
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            WrapperPlayClientCreativeInventoryAction action = new WrapperPlayClientCreativeInventoryAction(event);
            if (player.gamemode != GameMode.CREATIVE) return;

            boolean valid = action.getSlot() >= 1 &&
                    (PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_8) ?
                            action.getSlot() <= 45 : action.getSlot() < 45);

            if (valid) {
                inventory.getSlot(action.getSlot()).set(action.getItemStack());
                inventory.getInventoryStorage().handleClientClaimedSlotSet(action.getSlot());
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW && !event.isCancelled()) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);

            if (click.getWindowId() != openWindowID) {
                return;
            }

            if (menu instanceof NotImplementedMenu) {
                return;
            }

            Optional<Map<Integer, ItemStack>> slots = click.getSlots();
            slots.ifPresent(integerItemStackMap -> integerItemStackMap.keySet().forEach(this::markPlayerSlotAsChanged));

            int button = click.getButton();
            int slot = click.getSlot();
            WrapperPlayClientClickWindow.WindowClickType clickType = click.getWindowClickType();

            if (slot == -1 || slot == -999 || slot < menu.getSlots().size()) {
                menu.doClick(button, slot, clickType);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            this.closeActiveInventory();
        } else if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            this.startOfTickStack = getHeldItem();
        }
    }

    public void markSlotAsResyncing(BlockPlace place) {
        if (place.hand == InteractionHand.MAIN_HAND) {
            inventory.getInventoryStorage().handleClientClaimedSlotSet(Inventory.HOTBAR_OFFSET + player.packetStateData.lastSlotSelected);
        } else {
            inventory.getInventoryStorage().handleServerCorrectSlot(Inventory.SLOT_OFFHAND);
        }
    }

    public void onBlockPlace(BlockPlace place) {
        if (player.gamemode != GameMode.CREATIVE && place.itemStack.getType() != ItemTypes.POWDER_SNOW_BUCKET) {
            markSlotAsResyncing(place);
            place.itemStack.setAmount(place.itemStack.getAmount() - 1);
        }
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(event);

            MenuType menuType = MenuType.getMenuType(open.getType());

            AbstractContainerMenu newMenu;
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14)) {
                newMenu = MenuType.getMenuFromID(player, inventory, menuType);
            } else {
                newMenu = MenuType.getMenuFromString(player, inventory, open.getLegacyType(), open.getLegacySlots(), open.getHorseId());
            }

            packetSendingInventorySize = newMenu instanceof NotImplementedMenu ? UNSUPPORTED_INVENTORY_CASE : newMenu.getSlots().size();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                openWindowID = open.getContainerId();
                menu = newMenu;
                isPacketInventoryActive = !(newMenu instanceof NotImplementedMenu);
                needResend = newMenu instanceof NotImplementedMenu;
            });
        }

        if (event.getPacketType() == PacketType.Play.Server.OPEN_HORSE_WINDOW) {
            WrapperPlayServerOpenHorseWindow open = new WrapperPlayServerOpenHorseWindow(event);

            packetSendingInventorySize = UNSUPPORTED_INVENTORY_CASE;
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                isPacketInventoryActive = false;
                needResend = true;
                openWindowID = open.getWindowId();
            });
        }

        if (event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            packetSendingInventorySize = PLAYER_INVENTORY_CASE;

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), this::closeActiveInventory);
        }

        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems items = new WrapperPlayServerWindowItems(event);
            stateID = items.getStateId();

            List<ItemStack> slots = items.getItems();
            for (int i = 0; i < slots.size(); i++) {
                markServerForChangingSlot(i, items.getWindowId());
            }

            final int cachedPacketInvSize = packetSendingInventorySize;
            final AtomicBoolean updatedValue = new AtomicBoolean();
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (slots.size() == cachedPacketInvSize || items.getWindowId() == 0) {
                    isPacketInventoryActive = true;
                    updatedValue.set(true);
                }
            });

            if (items.getWindowId() == 0) {
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    if (!isPacketInventoryActive) return;
                    for (int i = 0; i < slots.size(); i++) {
                        inventory.getSlot(i).set(slots.get(i));
                    }
                    if (items.getCarriedItem().isPresent()) {
                        inventory.setCarried(items.getCarriedItem().get());
                    }
                });
            } else {
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    if (!isPacketInventoryActive) return;
                    if (items.getWindowId() == openWindowID) {
                        for (int i = 0; i < slots.size(); i++) {
                            menu.getSlot(i).set(slots.get(i));
                        }
                    }
                    if (items.getCarriedItem().isPresent()) {
                        inventory.setCarried(items.getCarriedItem().get());
                    }
                });
            }

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (updatedValue.get() && !menu.equals(inventory)) {
                    isPacketInventoryActive = false;
                }
            });
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory slot = new WrapperPlayServerSetPlayerInventory(event);
            final int slotID = slot.getSlot();
            final ItemStack item = slot.getStack();

            inventory.getInventoryStorage().handleServerCorrectSlot(slotID);

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (!isPacketInventoryActive) return;
                inventory.getSlot(slotID).set(item);
            });
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
            final int slotID = slot.getSlot();
            final int inventoryID = slot.getWindowId();
            final ItemStack item = slot.getItem();

            if (inventoryID == -2) {
                inventory.getInventoryStorage().handleServerCorrectSlot(slotID);
            } else if (inventoryID == 0) {
                inventory.getInventoryStorage().handleServerCorrectSlot(slotID);
            } else {
                markServerForChangingSlot(slotID, inventoryID);
            }

            stateID = slot.getStateId();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (!isPacketInventoryActive) return;
                if (inventoryID == -1) {
                    inventory.setCarried(item);
                } else if (inventoryID == -2) {
                    if (inventory.getInventoryStorage().getSize() > slotID && slotID >= 0) {
                        inventory.getInventoryStorage().setItem(slotID, item);
                    }
                } else if (inventoryID == 0) {
                    if (slotID >= 0 && slotID <= 45) {
                        inventory.getSlot(slotID).set(item);
                    }
                } else if (inventoryID == openWindowID) {
                    menu.getSlot(slotID).set(item);
                }
            });
        }
    }

    private void closeActiveInventory() {
        isPacketInventoryActive = true;
        openWindowID = 0;
        menu = inventory;
        menu.setCarried(ItemStack.EMPTY);
    }
}
