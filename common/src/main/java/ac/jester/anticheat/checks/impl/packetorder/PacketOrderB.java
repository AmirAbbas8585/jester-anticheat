package ac.jester.anticheat.checks.impl.packetorder;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckData(name = "PacketOrderB", description = "Did not swing for attack")
public class PacketOrderB extends Check implements PacketCheck {
    private final boolean is1_9 = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9);

    private final boolean exempt = player.getClientVersion().isOlderThan(ClientVersion.V_1_9)
            && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9);

    private boolean sentAnimationSinceLastAttack = player.getClientVersion().isNewerThan(ClientVersion.V_1_8);
    private boolean sentAttack, sentAnimation, sentSlotSwitch;

    public PacketOrderB(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (exempt) return;

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION
            && new WrapperPlayClientAnimation(event).getHand() == InteractionHand.MAIN_HAND) {
            sentAnimationSinceLastAttack = sentAnimation = true;
            sentAttack = sentSlotSwitch = false;
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                onAttack(event);
                return;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            onAttack(event);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
            if (packet.getAction() == DiggingAction.STAB) {
                onAttack(event);
                return;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE && !is1_9 && !sentSlotSwitch) {
            sentSlotSwitch = true;
            return;
        }

        if (!isAsync(event.getPacketType())) {
            if (sentAttack && is1_9) {
                flagAndAlert("post-attack");
            }

            sentAttack = sentAnimation = sentSlotSwitch = false;
        }
    }

    private void onAttack(PacketReceiveEvent event) {
        if (player.gamemode == GameMode.SPECTATOR && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_11)) return;

        sentAttack = true;

        if (is1_9 ? !sentAnimationSinceLastAttack : !sentAnimation) {
            sentAttack = false;
            if (flagAndAlert("pre-attack") && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }

        sentAnimationSinceLastAttack = sentAnimation = sentSlotSwitch = false;
    }
}
