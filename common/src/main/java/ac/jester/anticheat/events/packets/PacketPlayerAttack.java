package ac.jester.anticheat.events.packets;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityHorse;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import ac.jester.anticheat.checks.impl.badpackets.BadPacketsW;

public class PacketPlayerAttack extends PacketListenerAbstract {
    public PacketPlayerAttack() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());

            if (player == null) return;

            if (!player.compensatedEntities.entityMap.containsKey(interact.getEntityId()) && !player.compensatedEntities.serverPositionsMap.containsKey(interact.getEntityId())
                    && (!player.compensatedEntities.entitiesRemovedThisTick.contains(interact.getEntityId()) || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14))) {
                final BadPacketsW badPacketsW = player.checkManager.getCheck(BadPacketsW.class);
                if (badPacketsW.flagAndAlert("entityId=" + interact.getEntityId()) && badPacketsW.shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                return;
            }

            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                if (player.isResetItemUsageOnAttack()) {
                    GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                }

                if (player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0) return;

                ItemStack heldItem = player.inventory.getHeldItem();
                PacketEntity entity = player.compensatedEntities.getEntity(interact.getEntityId());

                if (entity != null && (!entity.isLivingEntity || entity.type == EntityTypes.PLAYER || entity.type == EntityTypes.PAINTING
                        || entity.type == EntityTypes.ENDER_DRAGON && player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2))) {
                    int knockbackLevel = player.getClientVersion().isOlderThan(ClientVersion.V_1_21) && heldItem != null
                            ? heldItem.getEnchantmentLevel(EnchantmentTypes.KNOCKBACK)
                            : 0;
                    final boolean hasNegativeKB = knockbackLevel < 0;

                    final boolean isLegacyPlayer = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8);
                    final boolean serverIs1_8 = PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9);
                    final boolean noCooldown = isLegacyPlayer || serverIs1_8
                            || player.attackCooldown.getMinimumProgress() > 0.9f;

                    if (!isLegacyPlayer) {
                        knockbackLevel = Math.max(knockbackLevel, 0);
                    }

                    if ((player.lastSprinting && !hasNegativeKB && noCooldown) || knockbackLevel > 0) {
                        player.minAttackSlow++;
                        player.maxAttackSlow++;

                        if (knockbackLevel == 0) {
                            player.maxAttackSlow = player.minAttackSlow = 1;
                        }
                    } else if (!isLegacyPlayer && player.lastSprinting) {
                        if (player.maxAttackSlow > 0
                                && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)
                                && player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_SPEED) < 16) {
                            return;
                        }

                        player.maxAttackSlow++;
                    }
                }
            } else if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT) {
                if (player.compensatedEntities.getEntity(interact.getEntityId()) instanceof PacketEntityHorse
                        && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_13)) {
                    player.packetStateData.horseInteractCausedForcedRotation = true;
                }
            }
        }
    }
}
