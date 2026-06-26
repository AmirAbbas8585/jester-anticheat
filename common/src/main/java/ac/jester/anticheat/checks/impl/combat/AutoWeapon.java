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
 * AutoWeapon — detects automatically switching to the best weapon before each attack.
 *
 * AutoWeapon (also called AutoSword, BestWeapon, or OptiFight in various clients)
 * automatically switches to the highest-damage weapon in the player's hotbar
 * immediately before sending an attack packet, then switches back afterward.
 *
 * Detection is per CLIENT tick, not wall-clock: the cheat's switch and attack
 * land in the SAME client tick, every attack. A legitimate player can also
 * scroll and click within one tick occasionally, so a single occurrence never
 * flags — we require minConsecutive (default 6) attacks in a row, each
 * preceded by a same-tick hotbar switch. Any attack without a same-tick
 * switch resets the streak, which a human inevitably produces.
 *
 * (The old wall-clock 50ms window false flagged scroll-then-click under
 * network jitter, which bunches packets from adjacent ticks together.)
 */
@CheckData(name = "AutoWeapon", description = "Automatically switching to optimal weapon before every attack")
public final class AutoWeapon extends Check implements PacketCheck {

    private boolean switchedThisTick = false;
    private int consecutiveSwitchAttacks = 0;

    private int minConsecutive = 6;

    public AutoWeapon(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = config.getIntElse("AutoWeapon.min-consecutive", 6);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Client tick boundary — switches don't carry over to the next tick
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            switchedThisTick = false;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            switchedThisTick = true;
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (switchedThisTick) {
            consecutiveSwitchAttacks++;
            if (consecutiveSwitchAttacks >= minConsecutive && player.isTickingReliablyFor(3)) {
                flagAndAlert(String.format("switch+attack same tick, consecutive=%d ping=%dms",
                        consecutiveSwitchAttacks, player.getTransactionPing()));
                consecutiveSwitchAttacks = 0;
            }
        } else {
            // Attack without a same-tick switch — human pattern, streak broken
            consecutiveSwitchAttacks = 0;
        }
    }
}
