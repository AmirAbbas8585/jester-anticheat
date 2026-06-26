package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.checks.impl.aim.AimDuplicateLook;
import ac.jester.anticheat.checks.impl.aim.AimModulo360;
import ac.jester.anticheat.checks.impl.aim.processor.AimProcessor;
import ac.jester.anticheat.checks.impl.badpackets.*;
import ac.jester.anticheat.checks.impl.breaking.*;
import ac.jester.anticheat.checks.impl.chat.ChatA;
import ac.jester.anticheat.checks.impl.chat.ChatB;
import ac.jester.anticheat.checks.impl.chat.ChatC;
import ac.jester.anticheat.checks.impl.chat.ChatD;
import ac.jester.anticheat.checks.impl.combat.AutoBlock;
import ac.jester.anticheat.checks.impl.combat.AutoClickerA;
import ac.jester.anticheat.checks.impl.combat.AutoClickerB;
import ac.jester.anticheat.checks.impl.combat.Criticals;
import ac.jester.anticheat.checks.impl.combat.Hitboxes;
import ac.jester.anticheat.checks.impl.combat.KillAuraA;
import ac.jester.anticheat.checks.impl.combat.KillAuraB;
import ac.jester.anticheat.checks.impl.combat.AutoTotem;
import ac.jester.anticheat.checks.impl.combat.KillAuraD;
import ac.jester.anticheat.checks.impl.combat.CrystalAura;
import ac.jester.anticheat.checks.impl.combat.FastBow;
import ac.jester.anticheat.checks.impl.combat.FastUse;
import ac.jester.anticheat.checks.impl.combat.KillAuraC;
import ac.jester.anticheat.checks.impl.combat.Multitask;
import ac.jester.anticheat.checks.impl.player.ChestStealer;
import ac.jester.anticheat.checks.impl.player.AutoFish;
import ac.jester.anticheat.checks.impl.player.AutoArmor;
import ac.jester.anticheat.checks.impl.player.AutoEat;
import ac.jester.anticheat.checks.impl.player.AutoRespawn;
import ac.jester.anticheat.checks.impl.breaking.NukerB;
import ac.jester.anticheat.checks.impl.breaking.AutoTool;
import ac.jester.anticheat.checks.impl.breaking.PacketMine;
import ac.jester.anticheat.checks.impl.combat.BedAura;
import ac.jester.anticheat.checks.impl.combat.AnchorAura;
import ac.jester.anticheat.checks.impl.combat.AutoPot;
import ac.jester.anticheat.checks.impl.combat.AutoWeapon;
import ac.jester.anticheat.checks.impl.scaffolding.ScaffoldGoDown;
import ac.jester.anticheat.checks.impl.combat.MultiInteractA;
import ac.jester.anticheat.checks.impl.combat.MultiInteractB;
import ac.jester.anticheat.checks.impl.combat.NoHitDelay;
import ac.jester.anticheat.checks.impl.combat.Reach;
import ac.jester.anticheat.checks.impl.combat.SelfInteract;
import ac.jester.anticheat.checks.impl.breaking.NukerA;
import ac.jester.anticheat.checks.impl.player.InventoryWalk;
import ac.jester.anticheat.checks.impl.crash.*;
import ac.jester.anticheat.checks.impl.elytra.*;
import ac.jester.anticheat.checks.impl.exploit.ExploitA;
import ac.jester.anticheat.checks.impl.exploit.ExploitB;
import ac.jester.anticheat.checks.impl.groundspoof.NoFall;
import ac.jester.anticheat.checks.impl.misc.ClientBrand;
import ac.jester.anticheat.checks.impl.misc.GhostBlockMitigation;
import ac.jester.anticheat.checks.impl.misc.Post;
import ac.jester.anticheat.checks.impl.misc.TransactionOrder;
import ac.jester.anticheat.checks.impl.movement.NoSlow;
import ac.jester.anticheat.checks.impl.movement.NoJumpDelay;
import ac.jester.anticheat.checks.impl.movement.AutoParkour;
import ac.jester.anticheat.checks.impl.movement.PredictionRunner;
import ac.jester.anticheat.checks.impl.misc.MeteorDetector;
import ac.jester.anticheat.checks.impl.movement.SetbackBlocker;
import ac.jester.anticheat.checks.impl.movement.VehiclePredictionRunner;
import ac.jester.anticheat.checks.impl.multiactions.*;
import ac.jester.anticheat.checks.impl.prediction.DebugHandler;
import ac.jester.anticheat.checks.impl.prediction.GroundSpoof;
import ac.jester.anticheat.checks.impl.prediction.OffsetHandler;
import ac.jester.anticheat.checks.impl.prediction.Phase;
import ac.jester.anticheat.checks.impl.scaffolding.*;
import ac.jester.anticheat.checks.impl.sprint.*;
import ac.jester.anticheat.checks.impl.timer.*;
import ac.jester.anticheat.checks.impl.vehicle.*;
import ac.jester.anticheat.checks.impl.velocity.ExplosionHandler;
import ac.jester.anticheat.checks.impl.velocity.KnockbackHandler;
import ac.jester.anticheat.checks.type.*;
import ac.jester.anticheat.events.packets.PacketChangeGameState;
import ac.jester.anticheat.events.packets.PacketEntityReplication;
import ac.jester.anticheat.events.packets.PacketPlayerAbilities;
import ac.jester.anticheat.events.packets.PacketWorldBorder;
import ac.jester.anticheat.manager.init.start.SuperDebug;
import ac.jester.anticheat.platform.api.permissions.PermissionDefaultValue;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.GhostBlockDetector;
import ac.jester.anticheat.predictionengine.SneakingEstimator;
import ac.jester.anticheat.utils.anticheat.update.*;
import ac.jester.anticheat.utils.latency.CompensatedCameraEntity;
import ac.jester.anticheat.utils.latency.CompensatedCooldown;
import ac.jester.anticheat.utils.latency.CompensatedFireworks;
import ac.jester.anticheat.utils.latency.CompensatedInventory;
import ac.jester.anticheat.utils.team.TeamHandler;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CheckManager {
    private static final AtomicBoolean initedAtomic = new AtomicBoolean(false);
    private static boolean inited;
    public final ClassToInstanceMap<AbstractCheck> allChecks;
    private final ClassToInstanceMap<PacketCheck> packetChecks;
    private final ClassToInstanceMap<PacketCheck> preViaPacketChecks;
    private final ClassToInstanceMap<PositionCheck> positionChecks;
    private final ClassToInstanceMap<RotationCheck> rotationChecks;
    private final ClassToInstanceMap<VehicleCheck> vehicleChecks;
    private final ClassToInstanceMap<PacketCheck> prePredictionChecks;
    private final ClassToInstanceMap<BlockBreakCheck> blockBreakChecks;
    private final ClassToInstanceMap<BlockPlaceCheck> blockPlaceChecks;
    private final ClassToInstanceMap<PostPredictionCheck> postPredictionChecks;
    private PacketEntityReplication packetEntityReplication = null;

    private final List<PacketCheck> packetChecksValues;
    private final List<PacketCheck> preViaPacketChecksValues;
    private final List<PositionCheck> positionChecksValues;
    private final List<RotationCheck> rotationChecksValues;
    private final List<VehicleCheck> vehicleChecksValues;
    private final List<PacketCheck> prePredictionChecksValues;
    private final List<BlockBreakCheck> blockBreakChecksValues;
    private final List<BlockPlaceCheck> blockPlaceChecksValues;
    private final List<PostPredictionCheck> postPredictionChecksValues;

    public CheckManager(GrimPlayer player) {
        // AimA listens to both packets (combat window) and rotations (GCD) —
        // one instance must live in both maps
        ac.jester.anticheat.checks.impl.aim.AimA aimA = new ac.jester.anticheat.checks.impl.aim.AimA(player);

        packetChecks = new ImmutableClassToInstanceMap.Builder<PacketCheck>()
                .put(CompensatedCameraEntity.class, player.cameraEntity)
                .put(ac.jester.anticheat.checks.impl.aim.AimA.class, aimA)
                .put(ac.jester.anticheat.checks.impl.combat.TriggerBot.class,
                        new ac.jester.anticheat.checks.impl.combat.TriggerBot(player))
                .put(ac.jester.anticheat.checks.impl.vehicle.BoatFly.class,
                        new ac.jester.anticheat.checks.impl.vehicle.BoatFly(player))
                .put(ac.jester.anticheat.checks.impl.vehicle.BoatClip.class,
                        new ac.jester.anticheat.checks.impl.vehicle.BoatClip(player))
                .put(ac.jester.anticheat.checks.impl.elytra.FireworkBoost.class,
                        new ac.jester.anticheat.checks.impl.elytra.FireworkBoost(player))
                .put(ac.jester.anticheat.checks.impl.misc.ResourcePackState.class,
                        new ac.jester.anticheat.checks.impl.misc.ResourcePackState(player))
                .put(Reach.class, new Reach(player))
                .put(AutoClickerA.class, new AutoClickerA(player))
                .put(AutoClickerB.class, new AutoClickerB(player))
                .put(KillAuraA.class, new KillAuraA(player))
                .put(KillAuraB.class, new KillAuraB(player))
                .put(KillAuraC.class, new KillAuraC(player))
                .put(FastBow.class, new FastBow(player))
                .put(FastUse.class, new FastUse(player))
                .put(Multitask.class, new Multitask(player))
                .put(CrystalAura.class, new CrystalAura(player))
                .put(AutoTotem.class, new AutoTotem(player))
                .put(KillAuraD.class, new KillAuraD(player))
                .put(ChestStealer.class, new ChestStealer(player))
                .put(AutoFish.class, new AutoFish(player))
                .put(AutoArmor.class, new AutoArmor(player))
                .put(AutoEat.class, new AutoEat(player))
                .put(AutoRespawn.class, new AutoRespawn(player))
                .put(BedAura.class, new BedAura(player))
                .put(AnchorAura.class, new AnchorAura(player))
                .put(AutoPot.class, new AutoPot(player))
                .put(AutoWeapon.class, new AutoWeapon(player))
                .put(MeteorDetector.class, new MeteorDetector(player))
                .put(SelfInteract.class, new SelfInteract(player))
                .put(AutoBlock.class, new AutoBlock(player))
                .put(Criticals.class, new Criticals(player))
                .put(NoHitDelay.class, new NoHitDelay(player))
                .put(AttackCooldownHandler.class, player.attackCooldown)
                .put(NukerA.class, new NukerA(player))
                .put(PacketEntityReplication.class, new PacketEntityReplication(player))
                .put(PacketChangeGameState.class, new PacketChangeGameState(player))
                .put(CompensatedInventory.class, player.inventory)
                .put(PacketPlayerAbilities.class, new PacketPlayerAbilities(player))
                .put(PacketWorldBorder.class, new PacketWorldBorder(player))
                .put(ActionManager.class, player.actionManager)
                .put(TeamHandler.class, new TeamHandler(player))
                .put(ClientBrand.class, new ClientBrand(player))
                .put(NoFall.class, new NoFall(player))
                .put(ChatA.class, new ChatA(player))
                .put(ChatB.class, new ChatB(player))
                .put(ChatC.class, new ChatC(player))
                .put(ChatD.class, new ChatD(player))
                .put(ExploitA.class, new ExploitA(player))
                .put(ExploitB.class, new ExploitB(player))
                .put(BadPacketsA.class, new BadPacketsA(player))
                .put(BadPacketsB.class, new BadPacketsB(player))
                .put(BadPacketsC.class, new BadPacketsC(player))
                .put(BadPacketsD.class, new BadPacketsD(player))
                .put(BadPacketsE.class, new BadPacketsE(player))
                .put(BadPacketsF.class, new BadPacketsF(player))
                .put(BadPacketsG.class, new BadPacketsG(player))
                .put(BadPacketsI.class, new BadPacketsI(player))
                .put(BadPacketsJ.class, new BadPacketsJ(player))
                .put(BadPacketsK.class, new BadPacketsK(player))
                .put(BadPacketsL.class, new BadPacketsL(player))
                .put(BadPacketsM.class, new BadPacketsM(player))
                .put(BadPacketsO.class, new BadPacketsO(player))
                .put(BadPacketsP.class, new BadPacketsP(player))
                .put(BadPacketsQ.class, new BadPacketsQ(player))
                .put(BadPacketsR.class, new BadPacketsR(player))
                .put(BadPacketsS.class, new BadPacketsS(player))
                .put(BadPacketsT.class, new BadPacketsT(player))
                .put(BadPacketsU.class, new BadPacketsU(player))
                .put(BadPacketsV.class, new BadPacketsV(player))
                .put(BadPacketsY.class, new BadPacketsY(player))
                .put(BadPacketsZ.class, new BadPacketsZ(player))
                .put(MultiActionsA.class, new MultiActionsA(player))
                .put(MultiActionsC.class, new MultiActionsC(player))
                .put(MultiActionsD.class, new MultiActionsD(player))
                .put(MultiActionsE.class, new MultiActionsE(player))
                .put(SprintA.class, new SprintA(player))
                .put(VehicleA.class, new VehicleA(player))
                .put(VehicleB.class, new VehicleB(player))
                .put(VehicleD.class, new VehicleD(player))
                .put(VehicleE.class, new VehicleE(player))
                .put(VehicleF.class, new VehicleF(player))
                .put(CrashB.class, new CrashB(player))
                .put(CrashD.class, new CrashD(player))
                .put(CrashE.class, new CrashE(player))
                .put(CrashF.class, new CrashF(player))
                .put(CrashH.class, new CrashH(player))
                .put(CrashI.class, new CrashI(player))
                .put(SetbackBlocker.class, new SetbackBlocker(player)) // Must be last class otherwise we can't check while blocking packets
                .build();

        positionChecks = new ImmutableClassToInstanceMap.Builder<PositionCheck>()
                .put(PredictionRunner.class, new PredictionRunner(player))
                .put(CompensatedCooldown.class, new CompensatedCooldown(player))
                .build();
        rotationChecks = new ImmutableClassToInstanceMap.Builder<RotationCheck>()
                .put(AimProcessor.class, new AimProcessor(player))
                .put(AimModulo360.class, new AimModulo360(player))
                .put(AimDuplicateLook.class, new AimDuplicateLook(player))
                .build();
        vehicleChecks = new ImmutableClassToInstanceMap.Builder<VehicleCheck>()
                .put(VehiclePredictionRunner.class, new VehiclePredictionRunner(player))
                .build();

        postPredictionChecks = new ImmutableClassToInstanceMap.Builder<PostPredictionCheck>()
                .put(NegativeTimer.class, new NegativeTimer(player))
                .put(ExplosionHandler.class, new ExplosionHandler(player))
                .put(KnockbackHandler.class, new KnockbackHandler(player))
                .put(GhostBlockDetector.class, new GhostBlockDetector(player))
                .put(Phase.class, new Phase(player))
                .put(Post.class, new Post(player))
                .put(GroundSpoof.class, new GroundSpoof(player))
                .put(OffsetHandler.class, new OffsetHandler(player))
                .put(SuperDebug.class, new SuperDebug(player))
                .put(DebugHandler.class, new DebugHandler(player))
                .put(BadPacketsX.class, new BadPacketsX(player))
                .put(NoSlow.class, new NoSlow(player))
                .put(SprintB.class, new SprintB(player))
                .put(SprintC.class, new SprintC(player))
                .put(SprintD.class, new SprintD(player))
                .put(SprintE.class, new SprintE(player))
                .put(SprintF.class, new SprintF(player))
                .put(SprintG.class, new SprintG(player))
                .put(MultiInteractA.class, new MultiInteractA(player))
                .put(MultiInteractB.class, new MultiInteractB(player))
                .put(NoJumpDelay.class, new NoJumpDelay(player))
                .put(AutoParkour.class, new AutoParkour(player))
                .put(ElytraA.class, new ElytraA(player))
                .put(ElytraB.class, new ElytraB(player))
                .put(ElytraC.class, new ElytraC(player))
                .put(ElytraD.class, new ElytraD(player))
                .put(ElytraE.class, new ElytraE(player))
                .put(ElytraF.class, new ElytraF(player))
                .put(ElytraG.class, new ElytraG(player))
                .put(ElytraH.class, new ElytraH(player))
                .put(ElytraI.class, new ElytraI(player))
                .put(InventoryWalk.class, new InventoryWalk(player))
                .put(SetbackTeleportUtil.class, new SetbackTeleportUtil(player)) // Avoid teleporting to new position, update safe pos last
                .put(CompensatedFireworks.class, player.fireworks)
                .put(SneakingEstimator.class, new SneakingEstimator(player))
                .put(LastInstanceManager.class, player.lastInstanceManager)
                .build();

        blockPlaceChecks = new ImmutableClassToInstanceMap.Builder<BlockPlaceCheck>()
                .put(ScaffoldGoDown.class, new ScaffoldGoDown(player))
                .put(InvalidPlaceA.class, new InvalidPlaceA(player))
                .put(InvalidPlaceB.class, new InvalidPlaceB(player))
                .put(AirLiquidPlace.class, new AirLiquidPlace(player))
                .put(FastPlace.class, new FastPlace(player))
                .put(Tower.class, new Tower(player))
                .put(MultiPlace.class, new MultiPlace(player))
                .put(MultiActionsF.class, new MultiActionsF(player))
                .put(MultiActionsG.class, new MultiActionsG(player))
                .put(BadPacketsH.class, new BadPacketsH(player))
                .put(CrashG.class, new CrashG(player))
                .put(FarPlace.class, new FarPlace(player))
                .put(FabricatedPlace.class, new FabricatedPlace(player))
                .put(PositionPlace.class, new PositionPlace(player))
                .put(RotationPlace.class, new RotationPlace(player))
                .put(DuplicateRotPlace.class, new DuplicateRotPlace(player))
                .put(GhostBlockMitigation.class, new GhostBlockMitigation(player))
                .build();

        prePredictionChecks = new ImmutableClassToInstanceMap.Builder<PacketCheck>()
                .put(Timer.class, new Timer(player))
                .put(TickTimer.class, new TickTimer(player))
                .put(TimerLimit.class, new TimerLimit(player))
                .put(CrashA.class, new CrashA(player))
                .put(CrashC.class, new CrashC(player))
                .put(VehicleTimer.class, new VehicleTimer(player))
                .build();

        blockBreakChecks = new ImmutableClassToInstanceMap.Builder<BlockBreakCheck>()
                .put(AirLiquidBreak.class, new AirLiquidBreak(player))
                .put(VeinMiner.class, new VeinMiner(player))
                .put(NukerB.class, new NukerB(player))
                .put(AutoTool.class, new AutoTool(player))
                .put(PacketMine.class, new PacketMine(player))
                .put(WrongBreak.class, new WrongBreak(player))
                .put(RotationBreak.class, new RotationBreak(player))
                .put(FastBreak.class, new FastBreak(player))
                .put(MultiBreak.class, new MultiBreak(player))
                .put(NoSwingBreak.class, new NoSwingBreak(player))
                .put(FarBreak.class, new FarBreak(player))
                .put(InvalidBreak.class, new InvalidBreak(player))
                .put(PositionBreakA.class, new PositionBreakA(player))
                .put(PositionBreakB.class, new PositionBreakB(player))
                .put(MultiActionsB.class, new MultiActionsB(player))
                .build();

        // Checks that run BEFORE ViaVersion packet translation (pre-Via pipeline)
        // Combat checks go here so they see raw entity IDs before Via remaps them
        preViaPacketChecks = new ImmutableClassToInstanceMap.Builder<PacketCheck>()
                .put(Reach.class, (Reach) packetChecks.get(Reach.class))
                .build();

        // All checks that have no listeners, generally invoked by other code to flag
        // TODO migrate more checks to here
        ClassToInstanceMap<AbstractCheck> noneModules = new ImmutableClassToInstanceMap.Builder<AbstractCheck>()
                // BadPacketsN/W + VehicleC + TransactionOrder are packet checks with no listener
                .put(BadPacketsN.class, new BadPacketsN(player))
                .put(BadPacketsW.class, new BadPacketsW(player))
                .put(TransactionOrder.class, new TransactionOrder(player))
                .put(VehicleC.class, new VehicleC(player))
                .put(Hitboxes.class, new Hitboxes(player)) // Hitboxes is invoked by Reach
                .build();

        allChecks = new ImmutableClassToInstanceMap.Builder<AbstractCheck>()
                .putAll(packetChecks)
                .putAll(positionChecks)
                .putAll(rotationChecks)
                .putAll(vehicleChecks)
                .putAll(postPredictionChecks)
                .putAll(blockPlaceChecks)
                .putAll(prePredictionChecks)
                .putAll(blockBreakChecks)
                .putAll(noneModules)
                .build();

        packetChecksValues = new ArrayList<>(packetChecks.values());
        preViaPacketChecksValues = new ArrayList<>(preViaPacketChecks.values());
        positionChecksValues = new ArrayList<>(positionChecks.values());
        rotationChecksValues = new ArrayList<>(rotationChecks.values());
        // AimA lives in packetChecks (and therefore allChecks — a class may only
        // appear ONCE across the maps or the allChecks putAll throws), but it
        // also needs rotation updates: add it to the dispatch list directly
        rotationChecksValues.add(aimA);
        vehicleChecksValues = new ArrayList<>(vehicleChecks.values());
        prePredictionChecksValues = new ArrayList<>(prePredictionChecks.values());
        blockBreakChecksValues = new ArrayList<>(blockBreakChecks.values());
        blockPlaceChecksValues = new ArrayList<>(blockPlaceChecks.values());
        postPredictionChecksValues = new ArrayList<>(postPredictionChecks.values());

        init();
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractCheck> T getCheck(Class<T> check) {
        return (T) allChecks.get(check);
    }

    @SuppressWarnings("unchecked")
    public <T extends PositionCheck> T getPositionCheck(Class<T> check) {
        return (T) positionChecks.get(check);
    }

    @SuppressWarnings("unchecked")
    public <T extends RotationCheck> T getRotationCheck(Class<T> check) {
        return (T) rotationChecks.get(check);
    }

    @SuppressWarnings("unchecked")
    public <T extends BlockPlaceCheck> T getBlockPlaceCheck(Class<T> check) {
        return (T) blockPlaceChecks.get(check);
    }

    public void onPreViaPacketReceive(final PacketReceiveEvent packet) {
        for (PacketCheck check : preViaPacketChecksValues) {
            check.onPacketReceive(packet);
        }
    }

    public void onPreViaPacketSend(final PacketSendEvent packet) {
        for (PacketCheck check : preViaPacketChecksValues) {
            check.onPacketSend(packet);
        }
    }

    public void onPrePredictionReceivePacket(final PacketReceiveEvent packet) {
        for (PacketCheck check : prePredictionChecksValues) {
            check.onPacketReceive(packet);
        }
    }

    public void onPacketReceive(final PacketReceiveEvent packet) {
        for (PacketCheck check : packetChecksValues) {
            check.onPacketReceive(packet);
        }
        for (PostPredictionCheck check : postPredictionChecksValues) {
            check.onPacketReceive(packet);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onPacketReceive(packet);
        }
        for (BlockBreakCheck check : blockBreakChecksValues) {
            check.onPacketReceive(packet);
        }
    }

    public void onPacketSend(final PacketSendEvent packet) {
        for (PacketCheck check : prePredictionChecksValues) {
            check.onPacketSend(packet);
        }
        for (PacketCheck check : packetChecksValues) {
            check.onPacketSend(packet);
        }
        for (PostPredictionCheck check : postPredictionChecksValues) {
            check.onPacketSend(packet);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onPacketSend(packet);
        }
        for (BlockBreakCheck check : blockBreakChecksValues) {
            check.onPacketSend(packet);
        }
    }

    public void onPositionUpdate(final PositionUpdate position) {
        for (PositionCheck check : positionChecksValues) {
            check.onPositionUpdate(position);
        }
    }

    public void onRotationUpdate(final RotationUpdate rotation) {
        for (RotationCheck check : rotationChecksValues) {
            check.process(rotation);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.process(rotation);
        }
    }

    public void onVehiclePositionUpdate(final VehiclePositionUpdate update) {
        for (VehicleCheck check : vehicleChecksValues) {
            check.process(update);
        }
    }

    public void onPredictionFinish(final PredictionComplete complete) {
        for (PostPredictionCheck check : postPredictionChecksValues) {
            check.onPredictionComplete(complete);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onPredictionComplete(complete);
        }
        for (BlockBreakCheck check : blockBreakChecksValues) {
            check.onPredictionComplete(complete);
        }
    }

    public void onBlockPlace(final BlockPlace place) {
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onBlockPlace(place);
        }
    }

    public void onPostFlyingBlockPlace(final BlockPlace place) {
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onPostFlyingBlockPlace(place);
        }
    }

    public void onBlockBreak(final BlockBreak blockBreak) {
        for (BlockBreakCheck check : blockBreakChecksValues) {
            check.onBlockBreak(blockBreak);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onBlockBreak(blockBreak);
        }
    }

    public void onPostFlyingBlockBreak(final BlockBreak blockBreak) {
        for (BlockBreakCheck check : blockBreakChecksValues) {
            check.onPostFlyingBlockBreak(blockBreak);
        }
        for (BlockPlaceCheck check : blockPlaceChecksValues) {
            check.onPostFlyingBlockBreak(blockBreak);
        }
    }

    public ExplosionHandler getExplosionHandler() {
        return getPostPredictionCheck(ExplosionHandler.class);
    }

    @SuppressWarnings("unchecked")
    public <T extends PacketCheck> T getPacketCheck(Class<T> check) {
        return (T) packetChecks.get(check);
    }

    @SuppressWarnings("unchecked")
    public <T extends PacketCheck> T getPrePredictionCheck(Class<T> check) {
        return (T) prePredictionChecks.get(check);
    }

    public PacketEntityReplication getEntityReplication() {
        if (packetEntityReplication == null)
            packetEntityReplication = getPacketCheck(PacketEntityReplication.class);
        return packetEntityReplication;
    }

    public NoFall getNoFall() {
        return getPacketCheck(NoFall.class);
    }

    public KnockbackHandler getKnockbackHandler() {
        return getPostPredictionCheck(KnockbackHandler.class);
    }

    public CompensatedCooldown getCompensatedCooldown() {
        return getPositionCheck(CompensatedCooldown.class);
    }

    public NoSlow getNoSlow() {
        return getPostPredictionCheck(NoSlow.class);
    }

    public SetbackTeleportUtil getSetbackUtil() {
        return getPostPredictionCheck(SetbackTeleportUtil.class);
    }

    public DebugHandler getDebugHandler() {
        return getPostPredictionCheck(DebugHandler.class);
    }

    public OffsetHandler getOffsetHandler() {
        return getPostPredictionCheck(OffsetHandler.class);
    }

    @SuppressWarnings("unchecked")
    public <T extends PostPredictionCheck> T getPostPredictionCheck(Class<T> check) {
        return (T) postPredictionChecks.get(check);
    }

    private void init() {
        if (inited || initedAtomic.getAndSet(true)) return;
        inited = true;

        final String[] permissions = {
                "jester.exempt.",
                "jester.nosetback.",
                "jester.nomodifypacket.",
        };

        for (final AbstractCheck check : allChecks.values()) {
            if (check.getConfigName() == null) continue;
            final String id = check.getConfigName().toLowerCase();
            for (String permissionName : permissions) {
                permissionName += id;
                GrimAPI.INSTANCE.getPermissionManager().registerPermission(permissionName, PermissionDefaultValue.FALSE);
            }
        }
    }
}
