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
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectateEntity;
import ac.jester.anticheat.checks.impl.badpackets.BadPacketsW;

public class PacketPlayerAttack extends PacketListenerAbstract {
    public PacketPlayerAttack() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            int entityId = interact.getEntityId();

            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                onAttack(event, player, entityId);
            } else {
                if (isInvalidEntity(event, player, entityId)) return;

                if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT) {
                    if (player.compensatedEntities.getEntity(entityId) instanceof PacketEntityHorse
                            && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_13)) {
                        player.packetStateData.horseInteractCausedForcedRotation = true;
                    }
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
            onAttack(event, player, packet.getEntityId());
        } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null || player.gamemode != GameMode.SPECTATOR) return;

            WrapperPlayClientSpectateEntity packet = new WrapperPlayClientSpectateEntity(event);
            if (!packet.hasEntityId()) return;
            onAttack(event, player, packet.getEntityId());
        }
    }

    private static boolean isInvalidEntity(PacketReceiveEvent event, GrimPlayer player, int entityId) {
        if (!player.compensatedEntities.entityMap.containsKey(entityId) && !player.compensatedEntities.serverPositionsMap.containsKey(entityId)
                && (!player.compensatedEntities.entitiesRemovedThisTick.contains(entityId) || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14))) {
            final BadPacketsW badPacketsW = player.checkManager.getCheck(BadPacketsW.class);
            if (badPacketsW.flagAndAlert("entityId=" + entityId) && badPacketsW.shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            return true;
        }
        return false;
    }

    private static void onAttack(PacketReceiveEvent event, GrimPlayer player, int entityId) {
        if (isInvalidEntity(event, player, entityId)) return;

        if (player.isResetItemUsageOnAttack()) {
            GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
        }

        if (player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0) return;

        ItemStack heldItem = player.inventory.getHeldItem();
        PacketEntity entity = player.compensatedEntities.getEntity(entityId);

        if (shouldPredictAttackSlow(player, entity)) {
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
    }

    private static boolean shouldPredictAttackSlow(GrimPlayer player, PacketEntity entity) {
        if (entity == null) return false;

        if (EntityTypes.isTypeInstanceOf(entity.type, EntityTypes.ABSTRACT_HANGING)) return false;

        return !entity.isLivingEntity || entity.type == EntityTypes.PLAYER
                || entity.type == EntityTypes.ENDER_DRAGON && player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2);
    }
}
