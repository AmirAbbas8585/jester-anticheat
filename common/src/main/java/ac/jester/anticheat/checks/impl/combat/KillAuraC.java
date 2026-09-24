package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "KillAura", configName = "KillAuraC",
        description = "Attacking entity while a container GUI is open (impossible in vanilla)")
public final class KillAuraC extends Check implements PacketCheck {
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
