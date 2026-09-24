package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "Criticals", description = "Fake critical hit via ground spoof during attack", setback = 5)
public final class Criticals extends Check implements PacketCheck {
    private int maxPingMs = 500;

    public Criticals(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxPingMs = config.getIntElse("Criticals.max-ping", 500);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (!player.isTickingReliablyFor(5)) return;
        if (player.getTransactionPing() > maxPingMs) return;

        if (!player.lastOnGround && player.onGround) return;

        if (!player.inVehicle() && player.onGround && !player.clientClaimsLastOnGround) {
            flagAndAlert(String.format("onGround=%b clientClaims=%b ping=%dms",
                    player.onGround, player.clientClaimsLastOnGround, player.getTransactionPing()));
        }
    }
}
