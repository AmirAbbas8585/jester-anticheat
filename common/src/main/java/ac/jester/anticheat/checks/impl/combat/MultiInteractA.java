package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.ArrayList;

@CheckData(name = "MultiInteractA", description = "Interacted with multiple entities in the same tick", experimental = true)
public class MultiInteractA extends Check implements PostPredictionCheck {
    private final ArrayList<String> flags = new ArrayList<>();
    private int lastEntity;
    private boolean lastSneaking;
    private boolean hasInteracted = false;
    // A single isolated tick of "switched entity" can be a real INTERACT_AT +
    // INTERACT pair against the same target with a sub-entity ID quirk, or one
    // tick of network jitter. MultiInteractB (the sibling right-click-aura
    // check) required this same fix for the same reason — only sustained
    // back-to-back occurrences are how an interact-aura actually behaves.
    private int consecutiveBadTicks = 0;
    private static final int MIN_CONSECUTIVE = 2;

    public MultiInteractA(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            int entity = packet.getEntityId();
            boolean sneaking = packet.isSneaking().orElse(false);

            // Backported from upstream Grim (2.0 branch, "actually check sneaking
            // in MultiInteractA"): a real interact-aura also toggles sneak state
            // between hits in some patterns; the original condition only ever
            // checked entity ID and silently ignored this case.
            if (hasInteracted && (entity != lastEntity || sneaking != lastSneaking)) {
                String verbose = "lastEntity=" + lastEntity + ", entity=" + entity
                        + ", lastSneaking=" + lastSneaking + ", sneaking=" + sneaking
                        + ", consecutive=" + (consecutiveBadTicks + 1);
                if (!player.canSkipTicks()) {
                    consecutiveBadTicks++;
                    if (consecutiveBadTicks >= MIN_CONSECUTIVE) {
                        if (flagAndAlert(verbose) && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                        consecutiveBadTicks = 0;
                    }
                } else {
                    flags.add(verbose);
                }
            } else if (hasInteracted) {
                consecutiveBadTicks = 0;
            }

            lastEntity = entity;
            lastSneaking = sneaking;
            hasInteracted = true;
        }

        if (!player.cameraEntity.isSelf() || isTickPacket(event.getPacketType())) {
            hasInteracted = false;
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (String verbose : flags) {
                flagAndAlert(verbose);
            }
        }

        flags.clear();
    }
}
