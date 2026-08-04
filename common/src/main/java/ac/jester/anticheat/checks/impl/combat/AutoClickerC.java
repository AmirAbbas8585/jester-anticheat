package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * AutoClickerC — an autoclicker farming mobs while the player is completely idle.
 *
 * The scenario the other click checks all miss: a player parks in front of a mob
 * farm, never moves and never turns, and a macro attacks every few seconds for
 * hours to collect drops or money. Nothing about it looks fast, so every
 * CPS-based check ignores it by construction:
 *   - AutoClickerA discards any interval over 2s outright and additionally needs
 *     over 6 CPS before its consistency test even runs.
 *   - AutoClickerB and NoHitDelay look for multiple packets inside ONE tick.
 *   - KillAuraD skips targets closer than 3 blocks, which farm mobs always are.
 *   - AimA only samples yaw deltas between 1 and 5 degrees, so a player who
 *     never turns produces no samples at all.
 * A four-second cadence sails through all of them.
 *
 * What this check keys on instead is the CONJUNCTION, which is what makes it
 * safe: sustained attacking, while the view is bit-identical, for minutes. Any
 * one of those alone has an innocent explanation; together they do not. A human
 * at a farm drifts the mouse, repositions, opens a chest to deposit, swaps or
 * repairs gear. Bit-identical yaw AND pitch across minutes is not something a
 * hand resting on a mouse produces — it is a machine holding the input.
 *
 * False-positive protections (this check kicks, so these matter):
 *   - ANY change in position, yaw or pitch resets everything. Not a threshold —
 *     an exact comparison, so the faintest real input clears the state.
 *   - Riding a vehicle is exempt: a boat or minecart legitimately freezes the
 *     player's rotation while they move.
 *   - Sitting (GSit and friends) is already exempt globally in Check.flag().
 *   - Container use, inventory clicks, dropping, block break/place and holding a
 *     different slot all count as real interaction and reset the streak.
 *   - Both a long stationary time AND a high attack count are required, so
 *     someone who simply stands still is never a candidate.
 *
 * NOTE ON INTERPRETATION: this detects a machine holding the input, which is not
 * quite the same claim as "the player installed a cheat client" — a weighted
 * mouse button produces the same packets. That is a rules question, not a
 * detection question, which is why it ships punishable but with a high
 * threshold; turn punishment off if your server permits AFK farming.
 */
@CheckData(name = "AutoClickerC", configName = "AutoClickerC",
        description = "Attacking on a fixed cadence while completely idle (AFK farm autoclicker)")
public final class AutoClickerC extends Check implements PacketCheck {

    private int minStationaryTicks = 6000; // 5 minutes
    private int minAttacks = 40;
    private int reflagEvery = 20;

    private double anchorX = Double.NaN;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;

    private int stationaryTicks;
    private int attacks;

    public AutoClickerC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minStationaryTicks = Math.max(20, config.getIntElse("AutoClickerC.min-stationary-ticks", 6000));
        minAttacks = Math.max(1, config.getIntElse("AutoClickerC.min-attacks", 40));
        reflagEvery = Math.max(1, config.getIntElse("AutoClickerC.reflag-every-attacks", 20));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();

        // Real interaction with the world — a machine parked at a farm does none
        // of this, and a human farming for hours inevitably does all of it.
        if (type == PacketType.Play.Client.CLICK_WINDOW
                || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || type == PacketType.Play.Client.PLAYER_DIGGING
                || type == PacketType.Play.Client.HELD_ITEM_CHANGE
                || type == PacketType.Play.Client.CLOSE_WINDOW
                || type == PacketType.Play.Client.CHAT_MESSAGE) {
            reset();
            return;
        }

        if (isTickPacketIncludingNonMovement(type)) {
            trackIdle();
            return;
        }

        if (type != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        // Only attacks landed while already provably idle count.
        if (stationaryTicks < minStationaryTicks) return;

        attacks++;
        if (attacks >= minAttacks && (attacks - minAttacks) % reflagEvery == 0) {
            flagAndAlert(String.format("idle=%ds attacks=%d yaw=%.1f pitch=%.1f",
                    stationaryTicks / 20, attacks, anchorYaw, anchorPitch));
        }
    }

    /** Called once per client tick: is the player still frozen in place? */
    private void trackIdle() {
        // A vehicle moves the player without any input from them and pins their
        // rotation, so "frozen" means nothing there.
        if (player.inVehicle()) {
            reset();
            return;
        }

        if (Double.isNaN(anchorX)) {
            anchor();
            return;
        }

        // Exact comparison on purpose. Any genuine mouse movement or step changes
        // these bits; only a machine reproduces them precisely tick after tick.
        boolean frozen = player.x == anchorX && player.y == anchorY && player.z == anchorZ
                && player.yaw == anchorYaw && player.pitch == anchorPitch;
        if (!frozen) {
            reset();
            anchor();
            return;
        }
        stationaryTicks++;
    }

    private void anchor() {
        anchorX = player.x;
        anchorY = player.y;
        anchorZ = player.z;
        anchorYaw = player.yaw;
        anchorPitch = player.pitch;
    }

    private void reset() {
        stationaryTicks = 0;
        attacks = 0;
    }
}
