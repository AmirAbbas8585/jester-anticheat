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

/**
 * Detects moving while an external CONTAINER GUI is open (InventoryMove / GUI Move).
 *
 * Vanilla locks movement INPUT while a container screen (chest, furnace, …) is
 * open. The player can still drift from prior momentum, knockback, or ice, but
 * those decay — only the cheat produces SUSTAINED held-key movement.
 *
 * IMPORTANT — what this can and cannot catch:
 *   - Container open (chest/furnace/etc.): the server knows the window is open
 *     (windowID != 0), so movement here is detectable. ✓
 *   - The player's OWN inventory (E key): opening it sends NO packet to the
 *     server, so the server cannot know it's open. Moving with only the E
 *     inventory open is undetectable by ANY packet anticheat — not a bug. ✗
 *
 * Enforcement: immediate setback (setback = 0) the moment the streak is hit,
 * and it keeps firing every tick the cheat stays active, so the player is
 * rubber-banded in place and the hack is unusable.
 */
@CheckData(name = "InventoryWalk", configName = "InventoryWalk", setback = 0,
        description = "Moving while a container GUI is open")
public final class InventoryWalk extends Check implements PostPredictionCheck {

    // Horizontal speed (blocks/tick) below this is residual drift, not input
    private double minSpeed = 0.10;
    // Consecutive moving ticks on the ground before flagging (~300ms)
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
        // Only judged while an external container is open
        if (player.inventory.getOpenWindowID() == 0) {
            consecutiveMoving = 0;
            return;
        }

        double deltaX = player.x - player.lastX;
        double deltaZ = player.z - player.lastZ;
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalSpeed < minSpeed) {
            // Drifted to a stop — momentum/knockback dies out, held keys don't
            consecutiveMoving = 0;
            return;
        }

        // Airborne: don't reset (so jump-spamming can't evade the streak) but
        // don't count it either — knockback launches you airborne and decays
        if (!player.onGround) {
            return;
        }

        // Ice keeps momentum for ~11 ticks legitimately — don't judge sliding
        if (onIce()) {
            consecutiveMoving = 0;
            return;
        }

        // GSit sitting/crawling/laying players move differently — exempt
        if (ExemptionProvider.safe().isSitting(player)) return;

        // Riding an entity: the vehicle moves the player with the GUI open
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
                // Immediate rubber-band; keeps firing every further tick the
                // cheat stays active, so GUI-move is neutralized, not just logged
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
