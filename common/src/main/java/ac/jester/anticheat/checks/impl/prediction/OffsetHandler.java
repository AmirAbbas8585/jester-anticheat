package ac.jester.anticheat.checks.impl.prediction;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.events.CompletePredictionEvent;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.nmsutil.Materials;

import java.util.concurrent.atomic.AtomicInteger;

@CheckData(name = "MovementA", decay = 0.02)
public class OffsetHandler extends Check implements PostPredictionCheck {
    private static final AtomicInteger flags = new AtomicInteger(0);
    private double setbackDecayMultiplier;
    private double threshold;
    private double sneakThreshold;
    private double carpetThreshold;
    private double bedThreshold;
    private double waterThreshold;
    private double pingThresholdPerMs;
    private int pingThresholdBaseline;
    private double immediateSetbackThreshold;
    private double maxAdvantage;
    private double maxCeiling;
    private double setbackViolationThreshold;
    private double advantageGained = 0;
    private long flightToggleGraceMs = 1000L;
    private int consecutiveOverThreshold = 0;

    public OffsetHandler(GrimPlayer player) {
        super(player);
    }

    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        double offset = predictionComplete.getOffset();

        CompletePredictionEvent completePredictionEvent = new CompletePredictionEvent(player, this, offset);
        GrimAPI.INSTANCE.getEventBus().post(completePredictionEvent);

        if (completePredictionEvent.isCancelled()) return;

        if (System.currentTimeMillis() - player.lastFlightToggleTime < flightToggleGraceMs) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        if (player.inJoinOrLoadGrace()) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().isSitting(player)) {
            advantageGained = 0;
            consecutiveOverThreshold = 0;
            removeOffsetLenience();
            return;
        }

        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCombatKnockback(player)) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        if (player.uncertaintyHandler.lastVehicleSwitch.hasOccurredSince(4)
                || player.uncertaintyHandler.lastTeleportTicks.hasOccurredSince(2)) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        double effectiveThreshold = player.isSneaking ? Math.max(threshold, sneakThreshold) : threshold;

        if (isOnCarpet()) {
            effectiveThreshold = Math.max(effectiveThreshold, carpetThreshold);
        }

        if (isOnOrNearBed()) {
            effectiveThreshold = Math.max(effectiveThreshold, bedThreshold);
        }

        if (player.wasTouchingWater) {
            effectiveThreshold = Math.max(effectiveThreshold, waterThreshold);
        }

        int pingOverBaseline = player.getTransactionPing() - pingThresholdBaseline;
        if (pingOverBaseline > 0) {
            effectiveThreshold += pingOverBaseline * pingThresholdPerMs;
        }

        if ((offset >= effectiveThreshold || offset >= immediateSetbackThreshold)) {
            advantageGained += offset;
            giveOffsetLenienceNextTick(offset);
            consecutiveOverThreshold++;

            boolean shouldFlagThisTick = consecutiveOverThreshold >= 2 || offset >= immediateSetbackThreshold;

            if (shouldFlagThisTick) {
                synchronized (flags) {
                    int flagId = (flags.get() & 255) + 1;

                    String humanFormattedOffset;
                    if (offset < 0.001) {
                        humanFormattedOffset = String.format("%.4E", offset);
                        humanFormattedOffset = humanFormattedOffset.replace("E-0", "E-");
                    } else {
                        humanFormattedOffset = String.format("%6f", offset);
                        humanFormattedOffset = humanFormattedOffset.replace("0.", ".");
                    }

                    String verbose = humanFormattedOffset + " /gl " + flagId
                            + " sneak=" + player.isSneaking + " carpet=" + isOnCarpet()
                            + " bed=" + isOnOrNearBed() + " water=" + player.wasTouchingWater;
                    if (flag(verbose)) {
                        if (alert(verbose)) {
                            flags.incrementAndGet();
                            predictionComplete.setIdentifier(flagId);
                        }

                        if ((advantageGained >= maxAdvantage || offset >= immediateSetbackThreshold)
                                && !isNoSetbackPermission()
                                && violations >= setbackViolationThreshold) {
                            player.getSetbackTeleportUtil().executeViolationSetback();
                        }
                    }
                }
            }

            advantageGained = Math.min(advantageGained, maxCeiling);
        } else {
            advantageGained *= setbackDecayMultiplier;
            consecutiveOverThreshold = 0;
        }

        removeOffsetLenience();
    }

    private boolean isOnCarpet() {
        return Materials.isCarpet(player.compensatedWorld.getBlockType(player.x, player.y - 0.1, player.z));
    }

    private boolean isOnOrNearBed() {
        double y = player.y - 0.1;
        return Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x + 1, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x - 1, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z + 1))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z - 1));
    }

    private void giveOffsetLenienceNextTick(double offset) {
        double minimizedOffset = Math.min(offset, 1);

        player.uncertaintyHandler.lastHorizontalOffset = minimizedOffset;
        player.uncertaintyHandler.lastVerticalOffset = minimizedOffset;
    }

    private void removeOffsetLenience() {
        player.uncertaintyHandler.lastHorizontalOffset = 0;
        player.uncertaintyHandler.lastVerticalOffset = 0;
    }

    @Override
    public void onReload(ConfigManager config) {
        setbackDecayMultiplier = config.getDoubleElse("MovementA.buildup-keep-ratio", 0.999);
        threshold = config.getDoubleElse("MovementA.min-offset", 0.001);
        sneakThreshold = config.getDoubleElse("MovementA.min-offset-sneak", 0.015);
        carpetThreshold = config.getDoubleElse("MovementA.min-offset-carpet", 0.02);
        bedThreshold = config.getDoubleElse("MovementA.min-offset-bed", 0.035);
        waterThreshold = config.getDoubleElse("MovementA.min-offset-water", 0.04);
        pingThresholdBaseline = config.getIntElse("MovementA.lag-grace-start-ms", 60);
        pingThresholdPerMs = config.getDoubleElse("MovementA.lag-grace-per-ms", 0.00003);
        immediateSetbackThreshold = config.getDoubleElse("MovementA.instant-rubberband-offset", 0.1);
        maxAdvantage = config.getDoubleElse("MovementA.rubberband-after-buildup", 1);
        maxCeiling = config.getDoubleElse("MovementA.buildup-limit", 4);
        setbackViolationThreshold = config.getDoubleElse("MovementA.rubberband-after-vl", 1);
        flightToggleGraceMs = config.getIntElse("MovementA.fly-toggle-grace-ms", 1000);
        if (maxAdvantage == -1) maxAdvantage = Double.MAX_VALUE;
        if (immediateSetbackThreshold == -1) immediateSetbackThreshold = Double.MAX_VALUE;
    }

    public boolean doesOffsetFlag(double offset) {
        return offset >= threshold;
    }
}
