// This file was designed and is an original check for JesterAC
// Copyright (C) 2021 DefineOutside
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntitySizeable;
import ac.jester.anticheat.utils.data.packetentity.dragon.PacketEntityEnderDragonPart;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemAttackRange;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// You may not copy the check unless you are licensed under GPL
@CheckData(name = "Reach", setback = 10)
public class Reach extends Check implements PacketCheck {
    private static final List<EntityType> blacklisted = Arrays.asList(
            EntityTypes.BOAT,
            EntityTypes.CHEST_BOAT,
            EntityTypes.SHULKER);
    private static final CheckResult NONE = new CheckResult(ResultType.NONE, "");
    private final Int2ObjectMap<InteractionData> playerAttackQueue = new Int2ObjectOpenHashMap<>();
    private boolean cancelImpossibleHits;
    private double threshold;
    private double sneakLenience;
    private double extraReach;
    private double cancelBuffer;

    public Reach(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (!player.disableGrim && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity action = new WrapperPlayClientInteractEntity(event);

            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
                player.onPacketCancel();
                return;
            }

            PacketEntity entity = player.compensatedEntities.entityMap.get(action.getEntityId());
            if (entity == null || entity instanceof PacketEntityEnderDragonPart) {
                if (shouldModifyPackets() && player.compensatedEntities.serverPositionsMap.containsKey(action.getEntityId())) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                return;
            }

            if (entity.isDead) return;

            if (entity.type == EntityTypes.ARMOR_STAND && player.getClientVersion().isOlderThan(ClientVersion.V_1_8))
                return;
            if (entity.type == EntityTypes.HAPPY_GHAST && player.getClientVersion().isOlderThan(ClientVersion.V_1_21_6)) {
                return;
            }
            if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR)
                return;
            if (player.inVehicle()) return;
            if (entity.riding != null) return;

            InteractionHand hand = action.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK ?
                    InteractionHand.MAIN_HAND : action.getHand();

            ItemStack currentStack = player.inventory.getItemInHand(hand);
            ItemStack startStack = player.inventory.getStartOfTickStack();

            boolean hasRange = false;
            float maxReach = 0f;
            float hitboxMargin = 0f;

            if (ATTACK_RANGE_COMPONENT_EXISTS) {
                ItemAttackRange startRange = startStack.getComponentOr(ComponentTypes.ATTACK_RANGE, null);

                if (startRange != null) {
                    ItemAttackRange currentRange = currentStack.getComponentOr(ComponentTypes.ATTACK_RANGE, null);
                    if (currentRange == null) {
                        hasRange = true;
                        maxReach = startRange.getMaxRange();
                        hitboxMargin = startRange.getHitboxMargin();
                    } else {
                        hasRange = true;
                        maxReach = Math.min(startRange.getMaxRange(), currentRange.getMaxRange());
                        hitboxMargin = Math.min(startRange.getHitboxMargin(), currentRange.getHitboxMargin());
                    }
                }
            }

            boolean tooManyAttacks = playerAttackQueue.size() > 10;
            if (!tooManyAttacks) {
                playerAttackQueue.put(action.getEntityId(), new InteractionData(
                        player.x, player.y, player.z,
                        hasRange, maxReach, hitboxMargin
                ));
            }

            boolean knownInvalid = isKnownInvalid(entity, hasRange, maxReach, hitboxMargin);

            if ((shouldModifyPackets() && cancelImpossibleHits && knownInvalid) || tooManyAttacks) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }

        if (isUpdate(event.getPacketType())) {
            tickBetterReachCheckWithAngle();
        }
    }

    private boolean isKnownInvalid(PacketEntity reachEntity, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin) {
        if ((blacklisted.contains(reachEntity.type) || !reachEntity.isLivingEntity) && reachEntity.type != EntityTypes.END_CRYSTAL)
            return false;

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR)
            return false;
        if (player.inVehicle()) return false;

        if (cancelBuffer != 0) {
            CheckResult result = checkReach(reachEntity, player.x, player.y, player.z, hasAttackRange, itemMaxReach, itemHitboxMargin, true);
            return result.isFlag();
        } else {
            SimpleCollisionBox targetBox = getTargetBox(reachEntity);

            double maxReach = applyReachModifiers(targetBox, hasAttackRange, itemMaxReach, itemHitboxMargin, !player.packetStateData.didLastMovementIncludePosition);

            return ReachUtils.getMinReachToBox(player, targetBox) > maxReach;
        }
    }

    private void tickBetterReachCheckWithAngle() {
        for (Int2ObjectMap.Entry<InteractionData> attack : playerAttackQueue.int2ObjectEntrySet()) {
            PacketEntity reachEntity = player.compensatedEntities.entityMap.get(attack.getIntKey());
            if (reachEntity == null) continue;

            InteractionData interactionData = attack.getValue();
            CheckResult result = checkReach(reachEntity, interactionData.x, interactionData.y, interactionData.z, interactionData.hasAttackRange, interactionData.maxReach, interactionData.hitboxMargin, false);
            switch (result.type()) {
                case REACH -> {
                    String added = ", type=" + reachEntity.type.getName().getKey();
                    if (reachEntity instanceof PacketEntitySizeable sizeable) {
                        added += ", size=" + sizeable.size;
                    }
                    flagAndAlert(result.verbose() + added);
                }
                case HITBOX -> {
                    String added = "type=" + reachEntity.type.getName().getKey();
                    if (reachEntity instanceof PacketEntitySizeable sizeable) {
                        added += ", size=" + sizeable.size;
                    }
                    player.checkManager.getCheck(Hitboxes.class).flagAndAlert(result.verbose() + added);
                }
            }
        }

        playerAttackQueue.clear();
    }

    private double getAttackRangeMovementAllowance(Vector3dm velocity, Vector3dm lookDir) {
        double dot = velocity.getX() * lookDir.getX() + velocity.getY() * lookDir.getY() + velocity.getZ() * lookDir.getZ();
        return Math.max(0, dot);
    }

    @NotNull
    private CheckResult checkReach(PacketEntity reachEntity, double x, double y, double z, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin, boolean isPrediction) {
        SimpleCollisionBox targetBox = getTargetBox(reachEntity);

        double maxReach = applyReachModifiers(targetBox, hasAttackRange, itemMaxReach, itemHitboxMargin, !player.packetStateData.didLastLastMovementIncludePosition);
        if (!isPrediction) {
            Vector3dm lookDir = ReachUtils.getLook(player, player.yaw, player.pitch);
            Vector3dm velocity = new Vector3dm(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ);
            maxReach += getAttackRangeMovementAllowance(velocity, lookDir);
        }
        double minDistance = Double.MAX_VALUE;

        List<Vector3dm> possibleLookDirs = new ArrayList<>(Collections.singletonList(ReachUtils.getLook(player, player.yaw, player.pitch)));

        if (!isPrediction) {
            possibleLookDirs.add(ReachUtils.getLook(player, player.lastYaw, player.pitch));

            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
                possibleLookDirs.add(ReachUtils.getLook(player, player.lastYaw, player.lastPitch));
            }

            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
                possibleLookDirs = Collections.singletonList(ReachUtils.getLook(player, player.yaw, player.pitch));
            }
        }

        final double distance = maxReach + 3;

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();
        final Vector3dm eyePos = new Vector3dm(x, 0, z);
        for (Vector3dm lookVec : possibleLookDirs) {
            for (double eye : possibleEyeHeights) {
                eyePos.setY(y + eye);
                Vector3dm endReachPos = eyePos.clone().add(lookVec.getX() * distance, lookVec.getY() * distance, lookVec.getZ() * distance);

                Vector3dm intercept = ReachUtils.calculateIntercept(targetBox, eyePos, endReachPos).first();

                if (ReachUtils.isVecInside(targetBox, eyePos)) {
                    minDistance = 0;
                    break;
                }

                if (intercept != null) {
                    minDistance = Math.min(eyePos.distance(intercept), minDistance);
                }
            }
        }

        if ((!blacklisted.contains(reachEntity.type) && reachEntity.isLivingEntity) || reachEntity.type == EntityTypes.END_CRYSTAL) {
            if (minDistance == Double.MAX_VALUE) {
                cancelBuffer = 1;
                return new CheckResult(ResultType.HITBOX, "");
            } else if (minDistance > maxReach) {
                cancelBuffer = 1;
                return new CheckResult(ResultType.REACH, String.format("%.5f", minDistance) + " blocks");
            } else {
                cancelBuffer = Math.max(0, cancelBuffer - 0.25);
            }
        }

        return NONE;
    }

    private SimpleCollisionBox getTargetBox(PacketEntity reachEntity) {
        if (reachEntity.type == EntityTypes.END_CRYSTAL) {
            return new SimpleCollisionBox(reachEntity.trackedServerPosition.getPos().subtract(1, 0, 1), reachEntity.trackedServerPosition.getPos().add(1, 2, 1));
        }
        return reachEntity.getPossibleCollisionBoxes();
    }

    private double applyReachModifiers(SimpleCollisionBox targetBox, boolean hasAttackRange, float itemMaxReach, float itemHitboxMargin, boolean giveMovementThreshold) {
        double maxReach;
        double hitboxMargin = threshold;

        if (hasAttackRange) {
            maxReach = itemMaxReach;
            hitboxMargin += itemHitboxMargin;
        } else {
            maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
                hitboxMargin += 0.1f;
            }
        }

        if (giveMovementThreshold || player.canSkipTicks()) {
            hitboxMargin += player.getMovementThreshold();
        }

        if (player.isSneaking) {
            hitboxMargin += sneakLenience;
        }

        targetBox.expand(hitboxMargin);

        return maxReach + extraReach;
    }

    private static final boolean ATTACK_RANGE_COMPONENT_EXISTS = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_11);

    @Override
    public void onReload(ConfigManager config) {
        this.cancelImpossibleHits = config.getBooleanElse("Reach.block-impossible-hits", true);
        this.threshold = config.getDoubleElse("Reach.threshold", 0.0005);
        this.sneakLenience = config.getDoubleElse("Reach.sneak-lenience", 0.1);
        this.extraReach = config.getDoubleElse("Reach.extra-reach", 0.3);
    }

    private enum ResultType {
        REACH, HITBOX, NONE
    }

    private record CheckResult(ResultType type, String verbose) {
        public boolean isFlag() {
            return type != ResultType.NONE;
        }
    }

    private record InteractionData(double x, double y, double z, boolean hasAttackRange, float maxReach, float hitboxMargin) {
    }
}
