package ac.jester.anticheat.utils.data.packetentity;

import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import ac.jester.anticheat.checks.impl.sprint.SprintD;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.attribute.ValuedAttribute;
import ac.jester.anticheat.utils.inventory.EnchantmentHelper;
import ac.jester.anticheat.utils.inventory.Inventory;
import ac.jester.anticheat.utils.math.GrimMath;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class PacketEntitySelf extends PacketEntity {
    private final GrimPlayer player;
    public int opLevel;

    public PacketEntitySelf(GrimPlayer player) {
        super(player, EntityTypes.PLAYER);
        this.trackEntityEquipment = true;
        this.player = player;
    }

    public PacketEntitySelf(GrimPlayer player, PacketEntitySelf old) {
        this(player);
        this.opLevel = old.opLevel;
        this.attributeMap.putAll(old.attributeMap);
    }

    @Override
    protected void initAttributes(GrimPlayer player) {
        super.initAttributes(player);
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
            setAttribute(Attributes.STEP_HEIGHT, 0.5f);
        }

        getAttribute(Attributes.SCALE).orElseThrow().withSetRewriter((oldValue, newValue) -> {
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_20_5) || (newValue).equals(oldValue)) {
                return oldValue;
            } else {
                player.possibleEyeHeights[2][0] = 0.4 * newValue;
                player.possibleEyeHeights[2][1] = 1.62 * newValue;
                player.possibleEyeHeights[2][2] = 1.27 * newValue;

                player.possibleEyeHeights[1][0] = 1.27 * newValue;
                player.possibleEyeHeights[1][1] = 1.62 * newValue;
                player.possibleEyeHeights[1][2] = 0.4 * newValue;

                player.possibleEyeHeights[0][0] = 1.62 * newValue;
                player.possibleEyeHeights[0][1] = 1.27 * newValue;
                player.possibleEyeHeights[0][2] = 0.4 * newValue;
                return newValue;
            }
        });

        final ValuedAttribute movementSpeed = ValuedAttribute.ranged(Attributes.MOVEMENT_SPEED, 0.1f, 0, 1024);
        movementSpeed.with(new WrapperPlayServerUpdateAttributes.Property(Attributes.MOVEMENT_SPEED, 0.1f, new ArrayList<>()));
        trackAttribute(movementSpeed);
        trackAttribute(ValuedAttribute.ranged(Attributes.ATTACK_DAMAGE, 2, 0, 2048));
        trackAttribute(ValuedAttribute.ranged(Attributes.ATTACK_SPEED, 4, 0, 1024)
                .requiredVersion(player, ClientVersion.V_1_9));
        trackAttribute(ValuedAttribute.ranged(Attributes.JUMP_STRENGTH, 0.42f, 0, 32)
                .requiredVersion(player, ClientVersion.V_1_20_5));
        trackAttribute(ValuedAttribute.ranged(Attributes.BLOCK_BREAK_SPEED, 1.0, 0, 1024)
                .requiredVersion(player, ClientVersion.V_1_20_5));
        trackAttribute(ValuedAttribute.ranged(Attributes.MINING_EFFICIENCY, 0, 0, 1024)
                .requiredVersion(player, ClientVersion.V_1_21));
        trackAttribute(ValuedAttribute.ranged(Attributes.SUBMERGED_MINING_SPEED, 0.2, 0, 20)
                .requiredVersion(player, ClientVersion.V_1_21));
        trackAttribute(ValuedAttribute.ranged(Attributes.ENTITY_INTERACTION_RANGE, 3, 0, 64)
                .requiredVersion(player, ClientVersion.V_1_20_5));
        trackAttribute(ValuedAttribute.ranged(Attributes.BLOCK_INTERACTION_RANGE, 4.5, 0, 64)
                .withGetRewriter(value -> {
                    if (player.gamemode == GameMode.CREATIVE
                            && (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)
                                || player.getClientVersion().isOlderThan(ClientVersion.V_1_20_5))) {
                        return 5.0;
                    }
                    return value;
                })
                .requiredVersion(player, ClientVersion.V_1_20_5));
        trackAttribute(ValuedAttribute.ranged(Attributes.WATER_MOVEMENT_EFFICIENCY, 0, 0, 1)
                .withGetRewriter(value -> {
                    if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
                        return 0d;
                    }

                    final double depthStrider = EnchantmentHelper.getMaximumEnchantLevel(player.inventory, EnchantmentTypes.DEPTH_STRIDER);
                    if (player.getClientVersion().isOlderThan(ClientVersion.V_1_21)) {
                        return depthStrider;
                    }

                    if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21)) {
                        return depthStrider / 3;
                    }

                    return value;
                })
                .requiredVersion(player, ClientVersion.V_1_21));
        trackAttribute(ValuedAttribute.ranged(Attributes.MOVEMENT_EFFICIENCY, 0, 0, 1)
                .requiredVersion(player, ClientVersion.V_1_21));
        trackAttribute(ValuedAttribute.ranged(Attributes.SNEAKING_SPEED, 0.3, 0, 1)
                .withGetRewriter(value -> {
                    if (player.getClientVersion().isOlderThan(ClientVersion.V_1_19)) {
                        return (double) 0.3f;
                    }

                    final int swiftSneak = player.inventory.getLeggings().getEnchantmentLevel(EnchantmentTypes.SWIFT_SNEAK);
                    final double clamped = GrimMath.clamp(0.3f + swiftSneak * 0.15f, 0f, 1f);
                    if (player.getClientVersion().isOlderThan(ClientVersion.V_1_21)) {
                        return clamped;
                    }

                    if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21)) {
                        return clamped;
                    }

                    return value;
                })
                .requiredVersion(player, ClientVersion.V_1_21));
    }

    public boolean inVehicle() {
        return getRiding() != null;
    }

    @Override
    public void addPotionEffect(PotionType effect, int amplifier) {
        if (effect == PotionTypes.BLINDNESS && !hasPotionEffect(PotionTypes.BLINDNESS)) {
            player.checkManager.getPostPredictionCheck(SprintD.class).startedSprintingBeforeBlind = player.isSprinting;
        }

        player.pointThreeEstimator.updatePlayerPotions(effect, amplifier);
        super.addPotionEffect(effect, amplifier);
    }

    @Override
    public void removePotionEffect(PotionType effect) {
        player.pointThreeEstimator.updatePlayerPotions(effect, null);
        super.removePotionEffect(effect);
    }

    @Override
    public void onFirstTransaction(boolean relative, boolean hasPos, double relX, double relY, double relZ,
                                   @Nullable Float packetXRot, @Nullable Float packetYRot, GrimPlayer player, @Nullable PositionPath path, boolean positionSync, int transaction) {
    }

    @Override
    public void onSecondTransaction(int transaction) {
    }

    @Override
    public SimpleCollisionBox getPossibleCollisionBoxes() {
        return player.boundingBox.copy();
    }

    @Override
    public void setItemBySlot(EquipmentSlot slot, ItemStack item) {
        if (slot == null || !player.inventory.isPacketInventoryActive && player.platformPlayer != null) return;
        if (item == null) item = ItemStack.EMPTY;
        switch (slot) {
            case MAIN_HAND -> player.inventory.inventory.setHeldItem(item);
            case OFF_HAND -> player.inventory.inventory.getInventoryStorage().setItem(Inventory.SLOT_OFFHAND, item);
            case BOOTS -> player.inventory.inventory.getInventoryStorage().setItem(Inventory.SLOT_BOOTS, item);
            case LEGGINGS -> player.inventory.inventory.getInventoryStorage().setItem(Inventory.SLOT_LEGGINGS, item);
            case CHEST_PLATE -> player.inventory.inventory.getInventoryStorage().setItem(Inventory.SLOT_CHESTPLATE, item);
            case HELMET -> player.inventory.inventory.getInventoryStorage().setItem(Inventory.SLOT_HELMET, item);
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == null) return ItemStack.EMPTY;
        return switch (slot) {
            case MAIN_HAND -> player.inventory.getHeldItem();
            case OFF_HAND -> player.inventory.getOffHand();
            case BOOTS -> player.inventory.getBoots();
            case LEGGINGS -> player.inventory.getLeggings();
            case CHEST_PLATE -> player.inventory.getChestplate();
            case HELMET -> player.inventory.getHelmet();
            case BODY, SADDLE -> ItemStack.EMPTY;
        };
    }

    @Override
    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !getItemBySlot(slot).isEmpty();
    }
}
