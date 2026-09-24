package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.hooks.ExemptionProvider;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@CheckData(name = "InventoryWalk", configName = "InventoryWalk", setback = 0,
        description = "Moving while a container GUI is open")
public final class InventoryWalk extends Check implements PostPredictionCheck {
    private double minSpeed = 0.10;
    private int minConsecutive = 6;

    private int consecutiveMoving = 0;

    public InventoryWalk(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minSpeed = config.getDoubleElse("InventoryWalk.min-speed", 0.10);
        minConsecutive = config.getIntElse("InventoryWalk.min-consecutive", 6);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (player.inventory.getOpenWindowID() == 0) {
            consecutiveMoving = 0;
            return;
        }

        double deltaX = player.x - player.lastX;
        double deltaZ = player.z - player.lastZ;
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalSpeed < minSpeed) {
            consecutiveMoving = 0;
            return;
        }

        if (!player.onGround) {
            return;
        }

        if (onIce()) {
            consecutiveMoving = 0;
            return;
        }

        if (ExemptionProvider.safe().isSitting(player)) return;

        if (player.compensatedEntities.self.getRiding() != null) {
            consecutiveMoving = 0;
            return;
        }

        if (player.getTransactionPing() > 400) return;

        consecutiveMoving++;
        if (consecutiveMoving >= minConsecutive && player.isTickingReliablyFor(3)) {
            if (flagAndAlert(String.format("windowId=%d speed=%.3f consecutive=%d ping=%dms",
                    player.inventory.getOpenWindowID(), horizontalSpeed,
                    consecutiveMoving, player.getTransactionPing()))) {
                setbackIfAboveSetbackVL();
            }
        }
    }

    private boolean onIce() {
        StateType below = player.compensatedWorld.getBlock(player.x, player.y - 0.1, player.z).getType();
        return below == StateTypes.ICE || below == StateTypes.PACKED_ICE
                || below == StateTypes.BLUE_ICE || below == StateTypes.FROSTED_ICE;
    }
}
