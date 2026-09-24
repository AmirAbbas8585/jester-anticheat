package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

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
        if (!hasPerTickMarker()) return;
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
            consecutiveSwitchAttacks = 0;
        }
    }
}
