package ac.jester.anticheat.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.GameMode;

@CheckData(name = "AutoParkour", description = "Pixel-perfect edge-jump timing consistent with AutoParkour")
public final class AutoParkour extends Check implements PostPredictionCheck {
    private double edgeThreshold = 0.70;
    private int windowSize = 8;
    private double minEdgeRatio = 0.875;
    private double minSpeedBps = 0.15;

    private int jumpBits = 0;
    private int jumpCount = 0;
    private int edgeCount = 0;

    public AutoParkour(GrimPlayer player) {
        super(player);
    }

    private boolean isLedgeAhead(double vx, double vz) {
        int x = (int) Math.floor(player.lastX);
        int y = (int) Math.floor(player.lastY);
        int z = (int) Math.floor(player.lastZ);
        if (Math.abs(vx) >= Math.abs(vz)) x += vx > 0 ? 1 : -1;
        else z += vz > 0 ? 1 : -1;
        return isOpen(x, y, z) && isOpen(x, y - 1, z);
    }

    private boolean isOpen(int x, int y, int z) {
        var type = player.compensatedWorld.getBlock(x, y, z).getType();
        return type == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.AIR
                || type == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CAVE_AIR
                || type == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.VOID_AIR;
    }

    @Override
    public void onReload(ConfigManager config) {
        edgeThreshold = config.getDoubleElse("AutoParkour.edge-threshold", 0.70);
        windowSize = Math.max(1, Math.min(31, config.getIntElse("AutoParkour.window-size", 8)));
        minEdgeRatio = config.getDoubleElse("AutoParkour.min-edge-ratio", 0.875);
        minSpeedBps = config.getDoubleElse("AutoParkour.min-speed-bps", 0.15);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;
        if (!player.isTickingReliablyFor(5)) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        if (!player.lastOnGround || player.onGround) return;

        double dy = player.y - player.lastY;
        if (dy < 0.05) return;

        double vx = player.x - player.lastX;
        double vz = player.z - player.lastZ;
        double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizontalSpeed < minSpeedBps) {
            jumpBits = 0;
            jumpCount = 0;
            edgeCount = 0;
            return;
        }

        double fracX = ((player.x % 1.0) + 1.0) % 1.0;
        double fracZ = ((player.z % 1.0) + 1.0) % 1.0;

        double edgeOffset;
        if (Math.abs(vx) >= Math.abs(vz)) {
            edgeOffset = (vx > 0) ? fracX : (1.0 - fracX);
        } else {
            edgeOffset = (vz > 0) ? fracZ : (1.0 - fracZ);
        }

        if (!isLedgeAhead(vx, vz)) return;

        boolean isEdgeJump = edgeOffset >= edgeThreshold;

        if (jumpCount >= windowSize) {
            if ((jumpBits & (1 << (windowSize - 1))) != 0) edgeCount--;
        } else {
            jumpCount++;
        }
        jumpBits = (jumpBits << 1) & ((1 << windowSize) - 1);
        if (isEdgeJump) {
            jumpBits |= 1;
            edgeCount++;
        }

        if (jumpCount >= windowSize) {
            double ratio = (double) edgeCount / windowSize;
            if (ratio >= minEdgeRatio && player.getTransactionPing() < 400) {
                flagAndAlert(String.format("edge_ratio=%.0f%% threshold=%.0f%% offset=%.2f speed=%.2f ping=%dms",
                        ratio * 100, minEdgeRatio * 100, edgeOffset, horizontalSpeed,
                        player.getTransactionPing()));
                jumpBits = 0;
                jumpCount = 0;
                edgeCount = 0;
            }
        }
    }
}
