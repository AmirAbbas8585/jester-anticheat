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
 * KillAura C — Attack while GUI inventory is open.
 *
 * In vanilla Minecraft, a player cannot attack while a container GUI (chest, furnace,
 * crafting table, etc.) is open because the game locks the attack input when the cursor
 * is captured by the inventory screen.
 *
 * KillAura cheats bypass this restriction and can attack entities even while the
 * player's inventory screen is open — a clear indicator of client-side modification.
 *
 * Note: window ID 0 is the player's hotbar/inventory which DOES allow attacking.
 * Only external containers (windowID != 0) lock the attack input.
 */
@CheckData(name = "KillAura", configName = "KillAuraC",
        description = "Attacking entity while a container GUI is open (impossible in vanilla)")
public final class KillAuraC extends Check implements PacketCheck {

    // Attacks in a row while a container is open before flagging. A real
    // killaura-through-GUI attacks continuously while the screen is open; a
    // single attack coinciding with a container just opening (a queued attack
    // that was in-flight when the server marked the window open) is a race, not
    // a cheat — requiring a streak removes that false positive.
    private int minConsecutive = 2;
    private int consecutive = 0;

    public KillAuraC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = Math.max(1, config.getIntElse("KillAuraC.min-consecutive", 2));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        // Only flag when a non-player container is open (windowID != 0). No
        // container open resets the streak.
        if (!player.inventory.hasExternalContainerOpen()) {
            consecutive = 0;
            return;
        }

        if (!player.isTickingReliablyFor(3)) return;

        if (++consecutive >= minConsecutive) {
            flagAndAlert("windowId=" + player.inventory.getOpenWindowID()
                    + " streak=" + consecutive + " ping=" + player.getTransactionPing() + "ms");
        }
    }
}
