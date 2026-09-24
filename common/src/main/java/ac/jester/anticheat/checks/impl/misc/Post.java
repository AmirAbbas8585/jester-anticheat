package ac.jester.anticheat.checks.impl.misc;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ANIMATION;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.CLICK_WINDOW;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ENTITY_ACTION;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.HELD_ITEM_CHANGE;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_ABILITIES;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_DIGGING;
import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.USE_ITEM;

@CheckData(name = "Post")
public class Post extends Check implements PacketCheck, PostPredictionCheck {
    private final ArrayDeque<PacketTypeCommon> post = new ArrayDeque<>();
    private final List<String> flags = new EvictingQueue<>(10);
    private boolean sentFlying = false;
    private int isExemptFromSwingingCheck = Integer.MIN_VALUE;

    public Post(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!flags.isEmpty()) {
            if (player.isTickingReliablyFor(3)) {
                for (String flag : flags) {
                    flagAndAlert(flag);
                }
            }

            flags.clear();
        }
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_ANIMATION) {
            WrapperPlayServerEntityAnimation animation = new WrapperPlayServerEntityAnimation(event);
            if (animation.getEntityId() == player.entityID) {
                if (animation.getType() == WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM ||
                        animation.getType() == WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND) {
                    isExemptFromSwingingCheck = player.lastTransactionSent.get();
                }
            }
        }
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            post.clear();
            sentFlying = true;
        } else {
            PacketTypeCommon packetType = event.getPacketType();
            if (isTransaction(packetType) && player.packetStateData.lastTransactionPacketWasValid) {
                if (sentFlying && !post.isEmpty()) {
                    flags.add(post.getFirst().toString().toLowerCase(Locale.ROOT).replace("_", " ") + " v" + player.getClientVersion().getReleaseName());
                }
                post.clear();
                sentFlying = false;
            } else if (PLAYER_ABILITIES.equals(packetType)
                    || (HELD_ITEM_CHANGE.equals(packetType) && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8))
                    || INTERACT_ENTITY.equals(packetType) || PLAYER_BLOCK_PLACEMENT.equals(packetType)
                    || USE_ITEM.equals(packetType) || PLAYER_DIGGING.equals(packetType)) {
                if (sentFlying) post.add(event.getPacketType());
            } else if (CLICK_WINDOW.equals(packetType) && player.getClientVersion().isOlderThan(ClientVersion.V_1_13)) {
                if (sentFlying) post.add(event.getPacketType());
            } else if (ANIMATION.equals(packetType)
                    && (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)
                    || PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_8_8))
                    && player.getClientVersion().isOlderThan(ClientVersion.V_1_13)
                    && isExemptFromSwingingCheck < player.lastTransactionReceived.get()) {
                if (sentFlying) post.add(event.getPacketType());
            } else if (ENTITY_ACTION.equals(packetType)
                    && (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) || new WrapperPlayClientEntityAction(event).getAction() != WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA)) {
                if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19_3) && player.inVehicle()) {
                    return;
                }
                if (sentFlying) post.add(event.getPacketType());
            }
        }
    }
}
