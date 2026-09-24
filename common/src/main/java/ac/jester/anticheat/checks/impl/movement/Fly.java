package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@CheckData(name = "Fly", configName = "Fly",
        description = "Staying airborne without falling, with no legitimate lift source")
public final class Fly extends Check implements PostPredictionCheck {
    private int minAirTicks = 40;
    private double fallingThreshold = -0.05;
    private double supportSearchDepth = 3.0;

    private int airTicks;
    private double lastY = Double.NaN;
    private int launchTicks;

    public Fly(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minAirTicks = Math.max(20, config.getIntElse("Fly.min-air-ticks", 40));
        fallingThreshold = config.getDoubleElse("Fly.falling-threshold", -0.05);
        supportSearchDepth = config.getDoubleElse("Fly.support-search-depth", 3.0);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        double launchY = pendingLaunchY();
        if (launchY > 0) {
            launchTicks = Math.max(launchTicks, risingTicks(launchY) + 5);
            airTicks = 0;
        }
        boolean rising = launchTicks > 0;
        if (rising) launchTicks--;

        if (isExempt()) {
            reset();
            return;
        }

        double previousY = lastY;
        lastY = player.y;
        if (Double.isNaN(previousY)) return;

        if (rising) return;

        if (player.y - previousY <= fallingThreshold) {
            airTicks = 0;
            return;
        }

        if (++airTicks < minAirTicks) return;

        flagAndAlert(String.format("airTicks=%d y=%.2f dY=%.4f ping=%dms",
                airTicks, player.y, player.y - previousY, player.getTransactionPing()));
        airTicks = 0;
    }

    private double pendingLaunchY() {
        double y = 0;
        for (var v : new ac.jester.anticheat.utils.data.VelocityData[]{
                player.likelyKB, player.firstBreadKB, player.likelyExplosions, player.firstBreadExplosion}) {
            if (v != null && v.vector != null && !v.isSetback) y = Math.max(y, v.vector.getY());
        }
        return y;
    }

    private static int risingTicks(double vy) {
        int ticks = 0;
        while (vy > 0 && ticks < 200) {
            vy = (vy - 0.08) * 0.98;
            ticks++;
        }
        return ticks;
    }

    private boolean isExempt() {
        if (player.canFly || player.isFlying) return true;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return true;

        if (player.onGround || player.lastOnGround) return true;

        if (player.isGliding || player.inVehicle() || player.isSwimming) return true;
        if (player.riptideSpinAttackTicks > 0) return true;
        if (player.fireworks.getMaxFireworksAppliedPossible() > 0) return true;

        if (player.isClimbing) return true;

        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.LEVITATION)) return true;
        if (player.compensatedEntities.self.hasPotionEffect(PotionTypes.SLOW_FALLING)) return true;

        if (player.inJoinOrLoadGrace()) return true;

        if (player.getSetbackTeleportUtil().blockOffsets) return true;

        return hasSupport();
    }

    private boolean hasSupport() {
        double x = player.x;
        double z = player.z;

        for (double dy = 0.0; dy <= supportSearchDepth; dy += 0.5) {
            if (isSupporting(x, player.y - dy, z)) return true;
        }
        double half = 0.31;
        for (double ox : new double[]{-half, half}) {
            for (double oz : new double[]{-half, half}) {
                for (double dy = 0.0; dy <= 1.5; dy += 0.5) {
                    if (isSupporting(x + ox, player.y - dy, z + oz)) return true;
                }
            }
        }

        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity == null || entity.isDead) continue;
            var pos = entity.trackedServerPosition.getPos();
            double dx = Math.abs(pos.getX() - x);
            double dz = Math.abs(pos.getZ() - z);
            double dy = player.y - pos.getY();
            if (dx <= 1.6 && dz <= 1.6 && dy >= -0.6 && dy <= 3.0) return true;
        }
        return false;
    }

    private boolean isSupporting(double x, double y, double z) {
        WrappedBlockState state = player.compensatedWorld.getBlock(x, y, z);
        StateType type = state.getType();
        if (type == StateTypes.AIR || type == StateTypes.CAVE_AIR || type == StateTypes.VOID_AIR) {
            return false;
        }
        return true;
    }

    private void reset() {
        airTicks = 0;
        lastY = Double.NaN;
    }
}
