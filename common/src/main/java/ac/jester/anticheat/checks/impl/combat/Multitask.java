package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "Multitask", description = "Attacking while actively using an item (eating/potion/etc)")
public final class Multitask extends Check implements PacketCheck {
    private int minConsecutive = 2;
    private int consecutive = 0;

    public Multitask(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = config.getIntElse("Multitask.min-consecutive", 2);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) return;

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (!player.packetStateData.isSlowedByUsingItem()) {
            consecutive = 0;
            return;
        }

        if (!player.isTickingReliablyFor(3)) return;
        if (player.getTransactionPing() > 600) return;

        consecutive++;
        if (consecutive >= minConsecutive) {
            flagAndAlert(String.format("attacks-while-using=%d ping=%dms",
                    consecutive, player.getTransactionPing()));
            consecutive = 0;
        }
    }
}
