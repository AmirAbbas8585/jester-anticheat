package ac.jester.anticheat.checks.impl.velocity;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.data.VectorData;
import ac.jester.anticheat.utils.data.VelocityData;
import ac.jester.anticheat.utils.math.Vector3dm;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.LinkedList;

@CheckData(name = "AntiKB", alternativeName = "AntiKnockback", configName = "Knockback", setback = 10, decay = 0.025)
public class KnockbackHandler extends Check implements PostPredictionCheck {
    private final Deque<VelocityData> firstBreadMap = new LinkedList<>();

    private final Deque<VelocityData> lastKnockbackKnownTaken = new LinkedList<>();
    private VelocityData firstBreadOnlyKnockback = null;
    @Getter
    private boolean knockbackPointThree = false;

    private double offsetToFlag;
    private double maxAdv, immediate, ceiling, multiplier;

    private double threshold;

    public KnockbackHandler(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity velocity = new WrapperPlayServerEntityVelocity(event);
            int entityId = velocity.getEntityId();

            if (player.compensatedEntities.serverPlayerVehicle != null && entityId != player.compensatedEntities.serverPlayerVehicle) {
                return;
            }
            if (player.compensatedEntities.serverPlayerVehicle == null && entityId != player.entityID) {
                return;
            }

            Vector3d playerVelocity = velocity.getVelocity();

            if (playerVelocity.getY() == -0.04) {
                velocity.setVelocity(playerVelocity.add(new Vector3d(0, 1 / 8000D, 0)));
                playerVelocity = velocity.getVelocity();
                event.markForReEncode(true);
            }

            playerVelocity = VectorPrecisionConverter.convert(player.getClientVersion(), playerVelocity);

            player.sendTransaction();
            addPlayerKnockback(entityId, player.lastTransactionSent.get(), new Vector3dm(playerVelocity.getX(), playerVelocity.getY(), playerVelocity.getZ()));
            event.getTasksAfterSend().add(player::sendTransaction);
        }
    }

    @NotNull
    public Pair<VelocityData, Vector3dm> getFutureKnockback() {
        if (!firstBreadMap.isEmpty()) {
            VelocityData data = firstBreadMap.peek();
            return new Pair<>(data, data != null ? data.vector : null);
        }

        if (!lastKnockbackKnownTaken.isEmpty()) {
            VelocityData data = lastKnockbackKnownTaken.peek();
            return new Pair<>(data, data != null ? data.vector : null);
        }

        if (player.firstBreadKB != null && player.likelyKB == null) {
            VelocityData data = player.firstBreadKB;
            return new Pair<>(data, data.vector.clone());
        } else if (player.likelyKB != null) {
            VelocityData data = player.likelyKB;
            return new Pair<>(data, data.vector.clone());
        }
        return new Pair<>(null, null);
    }

    private void addPlayerKnockback(int entityID, int breadOne, Vector3dm knockback) {
        firstBreadMap.add(new VelocityData(entityID, breadOne, player.getSetbackTeleportUtil().isSendingSetback, knockback));
    }

    public VelocityData calculateRequiredKB(int entityID, int transaction, boolean isJustTesting) {
        tickKnockback(transaction);

        VelocityData returnLastKB = null;
        for (VelocityData data : lastKnockbackKnownTaken) {
            if (data.entityID == entityID)
                returnLastKB = data;
        }

        if (!isJustTesting) {
            lastKnockbackKnownTaken.clear();
        }
        return returnLastKB;
    }

    private void tickKnockback(int transactionID) {
        firstBreadOnlyKnockback = null;
        if (firstBreadMap.isEmpty()) return;
        VelocityData data = firstBreadMap.peek();
        while (data != null) {
            if (data.transaction == transactionID) {
                firstBreadOnlyKnockback = new VelocityData(data.entityID, data.transaction, data.isSetback, data.vector);
                break;
            } else if (data.transaction < transactionID) {
                if (firstBreadOnlyKnockback != null) {
                    lastKnockbackKnownTaken.add(new VelocityData(data.entityID, data.transaction, data.vector, data.isSetback, data.offset));
                } else {
                    lastKnockbackKnownTaken.add(new VelocityData(data.entityID, data.transaction, data.isSetback, data.vector));
                }

                firstBreadOnlyKnockback = null;
                firstBreadMap.poll();
                data = firstBreadMap.peek();
            } else {
                break;
            }
        }
    }

    public void forceExempt() {
        if (player.firstBreadKB != null) {
            player.firstBreadKB.offset = 0;
        }

        if (player.likelyKB != null) {
            player.likelyKB.offset = 0;
        }
    }

    public void setPointThree(boolean isPointThree) {
        knockbackPointThree = knockbackPointThree || isPointThree;
    }

    public void handlePredictionAnalysis(double offset) {
        if (player.firstBreadKB != null) {
            player.firstBreadKB.offset = Math.min(player.firstBreadKB.offset, offset);
        }

        if (player.likelyKB != null) {
            player.likelyKB.offset = Math.min(player.likelyKB.offset, offset);
        }
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        double offset = predictionComplete.getOffset();
        if (!predictionComplete.isChecked() || predictionComplete.getData().isTeleport()) {
            forceExempt();
            return;
        }

        boolean wasZero = knockbackPointThree;
        knockbackPointThree = false;

        if (player.likelyKB == null && player.firstBreadKB == null) {
            return;
        }

        if (player.predictedVelocity.isFirstBreadKb()) {
            firstBreadOnlyKnockback = null;
            firstBreadMap.poll();
        }

        if (wasZero || player.predictedVelocity.isKnockback()) {
            if (player.firstBreadKB != null) {
                player.firstBreadKB.offset = Math.min(player.firstBreadKB.offset, offset);
            }

            if (player.likelyKB != null) {
                player.likelyKB.offset = Math.min(player.likelyKB.offset, offset);
            }
        }

        if (player.likelyKB != null) {
            if (player.likelyKB.offset > offsetToFlag) {
                threshold = Math.min(threshold + player.likelyKB.offset, ceiling);
                if (player.likelyKB.isSetback) {
                    if (!isNoSetbackPermission()) {
                        player.getSetbackTeleportUtil().executeViolationSetback();
                    }
                } else if (flagAndAlert(player.likelyKB.offset == Integer.MAX_VALUE ? "ignored knockback"
                        : "o: " + formatOffset(player.likelyKB.offset))) {
                    if (player.likelyKB.offset >= immediate || threshold >= maxAdv) {
                        setbackIfAboveSetbackVL();
                    }
                } else {
                    reward();
                }
            } else if (threshold > 0.05) {
                threshold *= multiplier;
            }
        }
    }

    public boolean shouldIgnoreForPrediction(VectorData data) {
        if (data.isKnockback() && data.isFirstBreadKb()) {
            return player.firstBreadKB.offset > offsetToFlag;
        }
        return false;
    }

    public boolean wouldFlag() {
        return (player.likelyKB != null && player.likelyKB.offset > offsetToFlag) || (player.firstBreadKB != null && player.firstBreadKB.offset > offsetToFlag);
    }

    public VelocityData calculateFirstBreadKnockback(int entityID, int transaction) {
        tickKnockback(transaction);
        if (firstBreadOnlyKnockback != null && firstBreadOnlyKnockback.entityID == entityID)
            return firstBreadOnlyKnockback;
        return null;
    }

    @Override
    public void onReload(ConfigManager config) {
        offsetToFlag = config.getDoubleElse("Knockback.min-offset", 0.001);
        maxAdv = config.getDoubleElse("Knockback.rubberband-after-buildup", 1);
        immediate = config.getDoubleElse("Knockback.instant-rubberband-offset", 0.1);
        multiplier = config.getDoubleElse("Knockback.buildup-keep-ratio", 0.999);
        ceiling = config.getDoubleElse("Knockback.buildup-limit", 4);
        if (maxAdv < 0) maxAdv = Double.MAX_VALUE;
        if (immediate < 0) immediate = Double.MAX_VALUE;
    }
}
