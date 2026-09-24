package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.HashSet;
import java.util.Set;

@CheckData(name = "KillAura", configName = "KillAuraB",
        description = "Attacking multiple distinct entities within a single client tick")
public final class KillAuraB extends Check implements PacketCheck {
    private final Set<Integer> entitiesThisTick = new HashSet<>();
    private int minTargets = 2;

    public KillAuraB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ac.grim.grimac.api.config.ConfigManager config) {
        minTargets = Math.max(2, config.getIntElse("KillAuraB.min-targets", 2));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!hasPerTickMarker()) return;
        if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            entitiesThisTick.clear();
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        entitiesThisTick.add(interact.getEntityId());

        if (entitiesThisTick.size() >= minTargets && player.isTickingReliablyFor(3)) {
            flagAndAlert(String.format("targets=%d ping=%dms",
                    entitiesThisTick.size(), player.getTransactionPing()));
            entitiesThisTick.clear();
        }
    }
}
