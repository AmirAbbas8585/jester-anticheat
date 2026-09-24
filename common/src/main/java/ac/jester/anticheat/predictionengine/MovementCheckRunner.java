package ac.jester.anticheat.predictionengine;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.hooks.ExemptionProvider;
import ac.jester.anticheat.checks.impl.prediction.Phase;
import ac.jester.anticheat.checks.impl.vehicle.VehicleC;
import ac.jester.anticheat.checks.type.PositionCheck;
import ac.jester.anticheat.manager.SetbackTeleportUtil;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerCamel;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerHappyGhast;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerHorse;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerNautilus;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerPig;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerPlayer;
import ac.jester.anticheat.predictionengine.movementtick.MovementTickerStrider;
import ac.jester.anticheat.predictionengine.predictions.PredictionEngineNormal;
import ac.jester.anticheat.predictionengine.predictions.rideable.PredictionEngineBoat;
import ac.jester.anticheat.predictionengine.predictions.rideable.PredictionEngineRideableUtils;
import ac.jester.anticheat.utils.anticheat.update.PositionUpdate;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.SetBackData;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityCamel;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityHappyGhast;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityHorse;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityNautilus;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityRideable;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityTrackXRot;
import ac.jester.anticheat.utils.enums.Pose;
import ac.jester.anticheat.utils.math.GrimMath;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.math.VectorUtils;
import ac.jester.anticheat.utils.nmsutil.BoundingBoxSize;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import ac.jester.anticheat.utils.nmsutil.Riptide;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

public class MovementCheckRunner extends Check implements PositionCheck {
    public static double predictionNanos = 0.3 * 1e6;
    public static double longPredictionNanos = 0.3 * 1e6;
    private boolean allowSprintJumpingWithElytra = true;

    public MovementCheckRunner(GrimPlayer player) {
        super(player);
    }

    public void processAndCheckMovementPacket(PositionUpdate data) {
        if (player.getSetbackTeleportUtil().insideUnloadedChunk()) {
            final boolean invalidVehicle = player.inVehicle() &&
                    (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9) ||
                            player.getClientVersion().isOlderThan(ClientVersion.V_1_9));

            if (!invalidVehicle && !data.isTeleport()) {
                player.getSetbackTeleportUtil().executeNonSimulatingForceResync();
            }
        }

        long start = System.nanoTime();
        check(data);
        long length = System.nanoTime() - start;

        if (!player.disableGrim) {
            predictionNanos = (predictionNanos * 499 / 500d) + (length / 500d);
            longPredictionNanos = (longPredictionNanos * 19999 / 20000d) + (length / 20000d);
        }
    }

    private void handleTeleport(PositionUpdate update) {
        player.lastX = player.x;
        player.lastY = player.y;
        player.lastZ = player.z;

        if (!player.inVehicle()) {
            if (update.getTeleportData() == null) {
                player.clientVelocity.setX(0);
                player.clientVelocity.setY(0);
                player.clientVelocity.setZ(0);
                player.lastWasClimbing = 0;
                player.canSwimHop = false;
            } else {
                final SetBackData setback = update.getSetback();
                if (setback == null || setback.getVelocity() == null) {
                    update.getTeleportData().modifyVector(player, player.clientVelocity);
                } else {
                    player.clientVelocity.setX(setback.getVelocity().getX());
                    player.clientVelocity.setY(setback.getVelocity().getY());
                    player.clientVelocity.setZ(setback.getVelocity().getZ());
                }
            }
        }

        player.uncertaintyHandler.lastTeleportTicks.reset();

        player.checkManager.getExplosionHandler().forceExempt();
        player.checkManager.getKnockbackHandler().forceExempt();

        player.boundingBox = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z);

        PredictionComplete predictionComplete = new PredictionComplete(0, update, true);
        player.getSetbackTeleportUtil().onPredictionComplete(predictionComplete);
        player.checkManager.getPostPredictionCheck(Phase.class).onPredictionComplete(predictionComplete);

        player.uncertaintyHandler.lastHorizontalOffset = 0;
        player.uncertaintyHandler.lastVerticalOffset = 0;
    }

    private void check(PositionUpdate update) {
        if (update.isTeleport()) {
            handleTeleport(update);
            return;
        }

        player.movementPackets++;

        player.onGround = update.isOnGround();

        if (!player.isFlying && player.isSneaking && Collisions.isAboveGround(player)) {
            double posX = Math.max(0.05, GrimMath.clamp(player.actualMovement.getX(), -16, 16) + 0.05);
            double posZ = Math.max(0.05, GrimMath.clamp(player.actualMovement.getZ(), -16, 16) + 0.05);
            double negX = Math.min(-0.05, GrimMath.clamp(player.actualMovement.getX(), -16, 16) - 0.05);
            double negZ = Math.min(-0.05, GrimMath.clamp(player.actualMovement.getZ(), -16, 16) - 0.05);

            Vector3dm NE = Collisions.maybeBackOffFromEdge(new Vector3dm(posX, 0, negZ), player, true);
            Vector3dm NW = Collisions.maybeBackOffFromEdge(new Vector3dm(negX, 0, negZ), player, true);
            Vector3dm SE = Collisions.maybeBackOffFromEdge(new Vector3dm(posX, 0, posZ), player, true);
            Vector3dm SW = Collisions.maybeBackOffFromEdge(new Vector3dm(negX, 0, posZ), player, true);

            boolean isEast = NE.getX() != posX || SE.getX() != posX;
            boolean isWest = NW.getX() != negX || SW.getX() != negX;
            boolean isNorth = NE.getZ() != negZ || NW.getZ() != negZ;
            boolean isSouth = SE.getZ() != posZ || SW.getZ() != posZ;

            if (isEast) player.uncertaintyHandler.lastStuckEast.reset();
            if (isWest) player.uncertaintyHandler.lastStuckWest.reset();
            if (isNorth) player.uncertaintyHandler.lastStuckNorth.reset();
            if (isSouth) player.uncertaintyHandler.lastStuckSouth.reset();

            if (isEast || isWest || isSouth || isNorth) {
                player.uncertaintyHandler.stuckOnEdge.reset();
            }
        }

        player.compensatedWorld.tickPlayerInPistonPushingArea();
        player.compensatedEntities.tick();

        if (player.vehicleData.wasVehicleSwitch || player.vehicleData.lastDummy) {
            player.uncertaintyHandler.lastVehicleSwitch.reset();
        }

        if (player.vehicleData.lastDummy) {
            player.clientVelocity.multiply(0.98);
        }

        final PacketEntity riding = player.compensatedEntities.self.getRiding();
        if (player.vehicleData.wasVehicleSwitch || player.vehicleData.lastDummy) {
            update.setTeleport(true);

            player.vehicleData.lastDummy = false;
            player.vehicleData.wasVehicleSwitch = false;

            if (riding != null) {
                SimpleCollisionBox interTruePositions = riding.getPossibleCollisionBoxes();

                final float scale = (float) riding.getAttributeValue(Attributes.SCALE);
                float width = BoundingBoxSize.getWidth(player, riding) * scale;
                float height = BoundingBoxSize.getHeight(player, riding) * scale;
                interTruePositions.expand(-width, 0, -width);
                interTruePositions.expandMax(0, -height, 0);

                Vector3dm cutTo = VectorUtils.cutBoxToVector(player.x, player.y, player.z, interTruePositions);

                player.lastX = cutTo.getX();
                player.lastY = cutTo.getY();
                player.lastZ = cutTo.getZ();

                player.boundingBox = GetBoundingBox.getCollisionBoxForPlayer(player, player.lastX, player.lastY, player.lastZ);
            } else {
                if (new Vector3dm(player.lastX, player.lastY, player.lastZ).distance(new Vector3dm(player.x, player.y, player.z)) > 3) {
                    player.getSetbackTeleportUtil().executeForceResync();
                }

                handleTeleport(update);

                if (player.isClimbing) {
                    Vector3dm ladder = player.clientVelocity.clone().setY(0.2);
                    PredictionEngineNormal.staticVectorEndOfTick(player, ladder);
                    player.lastWasClimbing = ladder.getY();
                }
                return;
            }
        }

        if (player.isInBed != player.lastInBed) {
            update.setTeleport(true);
        }
        player.lastInBed = player.isInBed;

        if (player.isInBed) return;

        if (player.packetStateData.showsDeathScreen) return;

        if (!player.inVehicle()) {
            player.speed = player.compensatedEntities.self.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double auraSpeedMult = ExemptionProvider.safe().getSpeedMultiplier(player);
            if (auraSpeedMult > 1.0) player.speed *= auraSpeedMult;
            if (player.hasGravity != player.playerEntityHasGravity) {
                player.pointThreeEstimator.updatePlayerGravity();
            }
            player.hasGravity = player.playerEntityHasGravity;
        }

        if (player.inVehicle()) {
            player.checkManager.getExplosionHandler().forceExempt();

            riding.setPositionRaw(player, new SimpleCollisionBox(player.x, player.y, player.z, player.x, player.y, player.z));

            if (riding instanceof PacketEntityTrackXRot boat) {
                boat.packetYaw = player.yaw;
                boat.interpYaw = player.yaw;
                boat.steps = 0;
            }

            if (player.hasGravity != riding.hasGravity) {
                player.pointThreeEstimator.updatePlayerGravity();
            }
            player.hasGravity = riding.hasGravity;

            if (riding instanceof PacketEntityRideable) {
                VehicleC vehicleC = player.checkManager.getCheck(VehicleC.class);

                ItemType requiredItem = riding.type == EntityTypes.PIG ? ItemTypes.CARROT_ON_A_STICK : ItemTypes.WARPED_FUNGUS_ON_A_STICK;
                ItemStack mainHand = player.inventory.getHeldItem();
                ItemStack offHand = player.inventory.getOffHand();

                boolean correctMainHand = mainHand.getType() == requiredItem;
                boolean correctOffhand = offHand.getType() == requiredItem;

                if (!correctMainHand && !correctOffhand) {
                    vehicleC.flagAndAlert();
                } else {
                    vehicleC.reward();
                }
            }
        }

        if (player.isFlying) {
            player.fallDistance = 0;
            player.uncertaintyHandler.lastFlyingTicks.reset();
        }

        player.isClimbing = Collisions.onClimbable(player, player.lastX, player.lastY, player.lastZ);

        player.clientControlledVerticalCollision = Math.abs(player.y % (1 / 64D)) < 0.00001;

        player.actualMovement = new Vector3dm(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ);

        if (player.isSprinting != player.lastSprinting) {
            player.compensatedEntities.hasSprintingAttributeEnabled = player.isSprinting;
        }

        player.lastJumping = player.isJumping;
        player.isJumping = player.packetStateData.knownInput.jump();

        boolean oldFlying = player.isFlying;
        boolean oldGliding = player.isGliding;
        boolean oldSprinting = player.isSprinting;
        boolean oldSneaking = player.isSneaking;

        if (player.inVehicle()) {
            player.isFlying = false;
            player.isGliding = false;
            player.isSprinting &= riding instanceof PacketEntityCamel;
            player.isSneaking = false;

            if (riding.type != EntityTypes.PIG && riding.type != EntityTypes.STRIDER) {
                player.isClimbing = false;
            }
        }

        if (!player.inVehicle()) {
            player.speed += player.compensatedEntities.hasSprintingAttributeEnabled ? player.speed * 0.3f : 0;
        }

        boolean clientClaimsRiptide = player.packetStateData.tryingToRiptide;
        if (player.packetStateData.tryingToRiptide) {
            long currentTime = System.currentTimeMillis();
            boolean isInWater = player.isInWaterOrRain();

            if (currentTime - player.packetStateData.lastRiptide < 450 || !isInWater) {
                player.packetStateData.tryingToRiptide = false;
            }

            player.packetStateData.lastRiptide = currentTime;
        }

        SimpleCollisionBox steppingOnBB = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z).expand(player.getMovementThreshold()).offset(0, -1, 0);
        Collisions.hasMaterial(player, steppingOnBB, (pair) -> {
            WrappedBlockState data = pair.first();
            if (data.getType() == StateTypes.SLIME_BLOCK && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8)
                    && Math.abs((pair.second().getY() + 1.0D) - player.y) <= player.getMovementThreshold()) {
                player.uncertaintyHandler.isSteppingOnSlime = true;
                player.uncertaintyHandler.isSteppingOnBouncyBlock = true;
            }
            if (data.getType() == StateTypes.HONEY_BLOCK) {
                if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_14)
                        && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8)) {
                    player.uncertaintyHandler.isSteppingOnBouncyBlock = true;
                }
                player.uncertaintyHandler.isSteppingOnHoney = true;
            }
            if (BlockTags.BEDS.contains(data.getType()) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_12)
                    && Math.abs((pair.second().getY() + 0.5625D) - player.y) <= player.getMovementThreshold()) {
                player.uncertaintyHandler.isSteppingOnBouncyBlock = true;
            }
            if (BlockTags.ICE.contains(data.getType())) {
                player.uncertaintyHandler.isSteppingOnIce = true;
            }
            if (data.getType() == StateTypes.BUBBLE_COLUMN && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
                player.uncertaintyHandler.isSteppingNearBubbleColumn = true;
            }
            if (data.getType() == StateTypes.SCAFFOLDING) {
                player.uncertaintyHandler.isSteppingNearScaffolding = true;
            }
            return false;
        });

        player.uncertaintyHandler.thisTickSlimeBlockUncertainty = player.uncertaintyHandler.nextTickSlimeBlockUncertainty;
        player.uncertaintyHandler.nextTickSlimeBlockUncertainty = 0;

        SimpleCollisionBox expandedBB = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.lastX, player.lastY, player.lastZ, 0.001f, 0.001f);

        if (player.actualMovement.lengthSquared() < 2500)
            expandedBB.expandToAbsoluteCoordinates(player.x, player.y, player.z);

        expandedBB.expand(Pose.STANDING.width / 2, 0, Pose.STANDING.width / 2);
        expandedBB.expandMax(0, Pose.STANDING.height, 0);

        boolean isGlitchy = player.uncertaintyHandler.isNearGlitchyBlock;

        player.uncertaintyHandler.isNearGlitchyBlock = player.getClientVersion().isOlderThan(ClientVersion.V_1_9)
                && Collisions.hasMaterial(player, expandedBB.copy().expand(0.2),
                checkData -> BlockTags.ANVIL.contains(checkData.first().getType())
                        || checkData.first().getType() == StateTypes.CHEST || checkData.first().getType() == StateTypes.TRAPPED_CHEST);

        player.uncertaintyHandler.isOrWasNearGlitchyBlock = isGlitchy || player.uncertaintyHandler.isNearGlitchyBlock;
        player.uncertaintyHandler.checkForHardCollision();

        if (player.isFlying != player.wasFlying)
            player.uncertaintyHandler.lastFlyingStatusChange.reset();

        if (!player.inVehicle() && (Math.abs(player.x) == 2.9999999E7D || Math.abs(player.z) == 2.9999999E7D)) {
            player.uncertaintyHandler.lastThirtyMillionHardBorder.reset();
        }

        if (player.isFlying && player.getClientVersion().isOlderThan(ClientVersion.V_1_13) && player.compensatedWorld.containsLiquid(player.boundingBox)) {
            player.uncertaintyHandler.lastUnderwaterFlyingHack.reset();
        }

        boolean couldBeStuckSpeed = Collisions.checkStuckSpeed(player, player.getMovementThreshold());
        boolean couldLeaveStuckSpeed = player.isPointThree() && Collisions.checkStuckSpeed(player, -player.getMovementThreshold());
        player.uncertaintyHandler.claimingLeftStuckSpeed = !player.inVehicle() && player.stuckSpeedMultiplier.getX() < 1 && !couldLeaveStuckSpeed;

        if (couldBeStuckSpeed) {
            player.uncertaintyHandler.lastStuckSpeedMultiplier.reset();
        }

        player.startTickClientVel = player.clientVelocity;

        boolean wasChecked = false;

        if (player.compensatedEntities.self.isDead || (riding != null && riding.isDead)) {
            player.predictedVelocity = new VectorData(new Vector3dm(), VectorData.VectorType.Dead);
            player.clientVelocity = new Vector3dm();
        } else if (player.disableGrim || (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_8) && player.gamemode == GameMode.SPECTATOR) || player.isFlying || (player.isExemptElytra() && player.isGliding)) {
            player.predictedVelocity = new VectorData(player.actualMovement, VectorData.VectorType.Spectator);
            player.clientVelocity = player.actualMovement.clone();
            player.gravity = 0;
            player.friction = 0.91f;
            PredictionEngineNormal.staticVectorEndOfTick(player, player.clientVelocity);
        } else if (riding == null) {
            wasChecked = true;

            player.depthStriderLevel = (float) player.compensatedEntities.self.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
            player.sneakingSpeedMultiplier = (float) player.compensatedEntities.self.getAttributeValue(Attributes.SNEAKING_SPEED);

            player.verticalCollision = false;

            if (player.lastOnGround && player.packetStateData.tryingToRiptide && !player.inVehicle()) {
                Vector3dm pushingMovement = Collisions.collide(player, 0, 1.1999999F, 0);
                player.verticalCollision = pushingMovement.getY() != 1.1999999F;
                double currentY = player.clientVelocity.getY();

                if (likelyGroundRiptide(pushingMovement)) {
                    player.uncertaintyHandler.thisTickSlimeBlockUncertainty = Math.abs(Riptide.getRiptideVelocity(player).getY()) + (currentY > 0 ? currentY : 0);
                    player.uncertaintyHandler.nextTickSlimeBlockUncertainty = Math.abs(Riptide.getRiptideVelocity(player).getY()) + (currentY > 0 ? currentY : 0);

                    player.lastOnGround = false;
                    player.lastY += pushingMovement.getY();
                    PlayerBaseTick.updatePlayerPose(player);
                    player.boundingBox = GetBoundingBox.getPlayerBoundingBox(player, player.lastX, player.lastY, player.lastZ);
                    player.actualMovement = new Vector3dm(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ);

                    player.couldSkipTick = true;

                    Collisions.handleInsideBlocks(player);
                }
            }

            PlayerBaseTick.doBaseTick(player);
            new MovementTickerPlayer(player).livingEntityAIStep();
            PlayerBaseTick.updatePowderSnow(player);
            PlayerBaseTick.updatePlayerPose(player);
        } else if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            wasChecked = true;
            if (riding.isBoat) {
                PlayerBaseTick.doBaseTick(player);
                new PredictionEngineBoat(player).guessBestMovement(0.1f, player);
            } else if (riding instanceof PacketEntityNautilus) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerNautilus(player).livingEntityAIStep();
            } else if (riding instanceof PacketEntityCamel) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerCamel(player).livingEntityAIStep();
            } else if (riding instanceof PacketEntityHappyGhast) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerHappyGhast(player).livingEntityAIStep();
            } else if (riding instanceof PacketEntityHorse) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerHorse(player).livingEntityAIStep();
            } else if (riding.type == EntityTypes.PIG) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerPig(player).livingEntityAIStep();
            } else if (riding.type == EntityTypes.STRIDER) {
                PlayerBaseTick.doBaseTick(player);
                new MovementTickerStrider(player).livingEntityAIStep();
                MovementTickerStrider.floatStrider(player);
                Collisions.handleInsideBlocks(player);
            } else {
                wasChecked = false;
            }
        }

        double offset = player.predictedVelocity.vector.distance(player.actualMovement);
        offset = player.uncertaintyHandler.reduceOffset(offset);

        if (player.packetStateData.tryingToRiptide != clientClaimsRiptide) {
            player.getSetbackTeleportUtil().executeForceResync();
        }

        if (player.getSetbackTeleportUtil().getRequiredSetBack() != null && player.getSetbackTeleportUtil().getRequiredSetBack().getTicksComplete() == 1) {
            Vector3dm setbackVel = player.getSetbackTeleportUtil().getRequiredSetBack().getVelocity();
            if (player.predictedVelocity.isJump()
                    && !player.wasTouchingLava && !player.wasTouchingWater
                    && ((setbackVel != null && setbackVel.getY() >= 0) || !Collisions.slowCouldPointThreeHitGround(player, player.lastX, player.lastY, player.lastZ))) {
                player.getSetbackTeleportUtil().executeForceResync();
            }
            boolean lavaBugFix = player.wasTouchingLava && player.predictedVelocity.isJump() &&
                    player.predictedVelocity.vector.getY() < 0.06 && player.predictedVelocity.vector.getY() > -0.02;
            if (!player.predictedVelocity.isKnockback() && !lavaBugFix && player.getSetbackTeleportUtil().getRequiredSetBack().getVelocity() != null) {
                player.getSetbackTeleportUtil().executeForceResync();
            }
        }

        if (player.getSetbackTeleportUtil().blockOffsets) offset = 0;

        if (player.skippedTickInActualMovement || !wasChecked)
            player.uncertaintyHandler.lastPointThree.reset();

        player.checkManager.onPredictionFinish(new PredictionComplete(offset, update, wasChecked));

        player.wasLastPredictionCompleteChecked = wasChecked;

        if (player.platformPlayer != null && player.isGliding && player.predictedVelocity.isJump() && player.isSprinting && !allowSprintJumpingWithElytra) {
            SetbackTeleportUtil.SetbackPosWithVector lastKnownGoodPosition = player.getSetbackTeleportUtil().lastKnownGoodPosition;
            lastKnownGoodPosition.setVector(lastKnownGoodPosition.getVector().multiply(0.6 * 0.91, 1, 0.6 * 0.91));
            player.getSetbackTeleportUtil().executeNonSimulatingSetback();
        }

        if (!wasChecked) {
            player.checkManager.getExplosionHandler().forceExempt();
            player.checkManager.getKnockbackHandler().forceExempt();
        }

        player.lastOnGround = player.onGround;
        player.lastSprinting = player.isSprinting;
        player.lastSprintingForSpeed = player.isSprinting;
        player.wasFlying = player.isFlying;
        player.wasGliding = player.isGliding;
        player.wasSwimming = player.isSwimming;
        player.wasSneaking = player.isSneaking;
        player.packetStateData.tryingToRiptide = false;

        if (player.inVehicle()) {
            player.isFlying = oldFlying;
            player.isGliding = oldGliding;
            player.isSprinting = oldSprinting;
            player.isSneaking = oldSneaking;
        }

        player.riptideSpinAttackTicks--;
        if (player.predictedVelocity.isTrident())
            player.riptideSpinAttackTicks = 20;

        player.uncertaintyHandler.lastMovementWasZeroPointZeroThree = !player.inVehicle() && player.skippedTickInActualMovement;
        player.uncertaintyHandler.lastMovementWasUnknown003VectorReset = !player.inVehicle() && player.couldSkipTick
                && player.predictedVelocity.isKnockback()
                && !player.predictedVelocity.isSetbackKb(player);
        player.couldSkipTick = false;

        player.uncertaintyHandler.wasZeroPointThreeVertically = !player.inVehicle() &&
                ((player.uncertaintyHandler.lastMovementWasZeroPointZeroThree && player.pointThreeEstimator.controlsVerticalMovement())
                        || !player.pointThreeEstimator.canPredictNextVerticalMovement() || !player.pointThreeEstimator.isWasAlwaysCertain());

        player.uncertaintyHandler.lastPacketWasGroundPacket = player.uncertaintyHandler.onGroundUncertain;
        player.uncertaintyHandler.onGroundUncertain = false;

        PredictionEngineRideableUtils.applyPendingJumps(player);

        player.vehicleData.vehicleForward = (float) Math.min(0.98, Math.max(-0.98, player.vehicleData.nextVehicleForward));
        player.vehicleData.vehicleHorizontal = (float) Math.min(0.98, Math.max(-0.98, player.vehicleData.nextVehicleHorizontal));

        player.dashableEntities.tick();

        player.minAttackSlow = 0;
        player.maxAttackSlow = 0;

        player.likelyKB = null;
        player.firstBreadKB = null;
        player.firstBreadExplosion = null;
        player.likelyExplosions = null;

        player.trigHandler.setOffset(offset);
        player.pointThreeEstimator.endOfTickTick();
    }

    private boolean likelyGroundRiptide(Vector3dm pushingMovement) {
        double riptideYResult = Riptide.getRiptideVelocity(player).getY();

        double riptideDiffToBase = Math.abs(player.actualMovement.getY() - riptideYResult);
        double riptideDiffToGround = Math.abs(player.actualMovement.getY() - riptideYResult - pushingMovement.getY());

        return riptideDiffToGround < riptideDiffToBase;
    }

    @Override
    public void onReload(ConfigManager config) {
        allowSprintJumpingWithElytra = config.getBooleanElse("exploit.allow-sprint-jumping-when-using-elytra", true);
    }
}
