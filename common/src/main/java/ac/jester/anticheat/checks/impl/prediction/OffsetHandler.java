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

@CheckData(name = "Simulation", decay = 0.02)
public class OffsetHandler extends Check implements PostPredictionCheck {
    private static final AtomicInteger flags = new AtomicInteger(0);
    // Config
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
    // Current advantage gained
    private double advantageGained = 0;
    // Grace after a flight (/fly) toggle — the ability desync spikes the offset.
    // Configurable (Simulation.flight-toggle-grace-ms) for servers whose flight
    // plugins resync slower/faster than vanilla.
    private long flightToggleGraceMs = 1000L;
    // Consecutive ticks in a row where offset exceeded the threshold. A single
    // isolated tick over threshold is extremely common physics noise (block-edge
    // ledges, half-blocks, ViaBackwards translation, latency micro-jitter) and is
    // NOT how a real speed/fly hack looks — a real hack sustains the offset every
    // tick for as long as it's active. Only count a violation once we've seen 2+
    // in a row; isolated blips still decay advantageGained normally (so they
    // can't be chained for free) but don't add to the kick-counting VL.
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

        // Just toggled flight (/fly): client and server briefly disagree on the
        // ability bit, spiking the prediction offset. Decay and skip — never flag
        // speed/fly during this window. Real flight is predicted fine once synced.
        if (System.currentTimeMillis() - player.lastFlightToggleTime < flightToggleGraceMs) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        // Just joined (may spawn mid-air, chunks loading) or downloading a
        // resource pack (client can freeze) — don't flag the transient desync.
        if (player.inJoinOrLoadGrace()) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        // GSit seats move the player's actual position/hitbox out from under the
        // prediction engine. flag() already blocks recording a violation while
        // seated, but without this check advantageGained kept silently piling up
        // for the entire time the player sat (could be minutes), invisible until
        // the moment they stand up. The very first jump right after standing then
        // hit an already-maxed advantageGained plus a few real desync ticks while
        // GSit's dismount teleport resettles — instant kick. Resetting the buffer
        // (not just decaying it) while seated/just-stood-up removes that landmine.
        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().isSitting(player)) {
            advantageGained = 0;
            consecutiveOverThreshold = 0;
            removeOffsetLenience();
            return;
        }

        // Combat knockback (vanilla or a plugin's custom KB, e.g. DeluxeCombat)
        // applies a velocity the general movement predictor can mispredict for
        // a tick or two — unlike AntiKB, which reads the actual sent velocity
        // packet directly, Simulation predicts movement holistically and a
        // non-trivial velocity jump can produce an arbitrarily large one-tick
        // offset that a raised threshold alone wouldn't reliably cover. Real
        // logs showed large (~0.14-0.33), isolated, one-off Simulation spikes
        // with no other explanation (not sneaking/carpet/bed/water) — this hook
        // existed but was never actually wired to anything until now.
        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().hasRecentCombatKnockback(player)) {
            advantageGained *= setbackDecayMultiplier;
            removeOffsetLenience();
            return;
        }

        // While sneaking (vanilla shift), movement is slow and the prediction is
        // noticeably noisier near block edges — especially on ViaBackwards/1.8
        // clients whose sneak movement differs. A speed/fly cheat is pointless at
        // sneak speed, so a higher sneak threshold absorbs those false positives
        // without weakening real detection (which matters at full speed).
        double effectiveThreshold = player.isSneaking ? Math.max(threshold, sneakThreshold) : threshold;

        // Carpet is a 1/16-block-tall collision shape sitting on top of whatever
        // is beneath it. Real logs showed a sustained ~0.015-0.018 offset while
        // just walking normally (not sneaking) on carpet — Grim's simulated
        // collision for this thin shape doesn't perfectly match the client here.
        // Not a speed exploit (carpet doesn't change movement speed), so it's
        // safe to tolerate the same way sneaking is.
        if (isOnCarpet()) {
            effectiveThreshold = Math.max(effectiveThreshold, carpetThreshold);
        }

        // Beds have an asymmetric, non-full-cube collision shape (head/foot
        // halves at reduced height) — real logs showed a sustained, repeating
        // ~0.028-0.031 offset from jump-spamming on/near a bed corner (the same
        // exact value recurring, the same deterministic-engine-quirk signature
        // as carpet). Not a speed exploit — beds don't change movement speed.
        if (isOnOrNearBed()) {
            effectiveThreshold = Math.max(effectiveThreshold, bedThreshold);
        }

        // Water buoyancy/current/swimming-pose physics is some of the most
        // complex and historically false-positive-prone movement in vanilla to
        // simulate exactly (current pushes, depth strider, dolphin's grace,
        // swim-pose hitbox changes). A speed/fly cheat is also far less useful
        // underwater. Tolerate the same way sneaking/carpet are tolerated.
        if (player.wasTouchingWater) {
            effectiveThreshold = Math.max(effectiveThreshold, waterThreshold);
        }

        // Higher ping means more uncertainty in exactly when an input was
        // applied relative to the server's tick — that uncertainty shows up as
        // small extra prediction offset that isn't cheating. Scale a small
        // amount of extra tolerance in per ms of ping above the baseline (most
        // false positives reported this session came from players around
        // 70-110ms, plus PojavLauncher's mobile-network jitter).
        int pingOverBaseline = player.getTransactionPing() - pingThresholdBaseline;
        if (pingOverBaseline > 0) {
            effectiveThreshold += pingOverBaseline * pingThresholdPerMs;
        }

        if ((offset >= effectiveThreshold || offset >= immediateSetbackThreshold)) {
            advantageGained += offset;
            giveOffsetLenienceNextTick(offset);
            consecutiveOverThreshold++;

            // A single isolated tick (consecutiveOverThreshold == 1) is not flagged
            // unless it's already huge enough to hit the immediate-setback threshold
            // on its own — that case is unambiguous and must never be skipped.
            boolean shouldFlagThisTick = consecutiveOverThreshold >= 2 || offset >= immediateSetbackThreshold;

            if (shouldFlagThisTick) {
                synchronized (flags) {
                    int flagId = (flags.get() & 255) + 1; // 1-256 as possible values

                    String humanFormattedOffset;
                    if (offset < 0.001) { // 1.129E-3
                        humanFormattedOffset = String.format("%.4E", offset);
                        // Squeeze out an extra digit here by E-03 to E-3
                        humanFormattedOffset = humanFormattedOffset.replace("E-0", "E-");
                    } else {
                        // 0.00112945678 -> .001129
                        humanFormattedOffset = String.format("%6f", offset);
                        // I like the leading zero, but removing it lets us add another digit to the end
                        humanFormattedOffset = humanFormattedOffset.replace("0.", ".");
                    }

                    // sneak=/carpet= are appended so future logs can confirm whether
                    // a flagged player was actually sneaking or on carpet, instead of
                    // guessing from gameplay context — settles it directly from the data.
                    String verbose = humanFormattedOffset + " /gl " + flagId
                            + " sneak=" + player.isSneaking + " carpet=" + isOnCarpet()
                            + " bed=" + isOnOrNearBed() + " water=" + player.wasTouchingWater;
                    if (flag(verbose)) {
                        if (alert(verbose)) {
                            flags.incrementAndGet(); // This debug was sent somewhere
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
        // Carpet's collision top sits 0.0625 blocks above its own integer Y —
        // sampling a little below the player's feet lands inside the carpet
        // block itself (rather than the block under it) in the common case.
        return Materials.isCarpet(player.compensatedWorld.getBlockType(player.x, player.y - 0.1, player.z));
    }

    private boolean isOnOrNearBed() {
        // Check the feet block plus the 4 cardinal neighbors — a bed is two
        // block-spaces (head+foot), and jumping right at the seam/corner
        // between them (or the edge into an adjacent block) is exactly the
        // scenario that produced the offset in real logs, not just standing
        // dead-center on one bed block.
        double y = player.y - 0.1;
        return Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x + 1, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x - 1, y, player.z))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z + 1))
                || Materials.isBed(player.compensatedWorld.getBlockType(player.x, y, player.z - 1));
    }

    private void giveOffsetLenienceNextTick(double offset) {
        // Don't let players carry more than 1 offset into the next tick
        // (I was seeing cheats try to carry 1,000,000,000 offset into the next tick!)
        //
        // This value so that setting back with high ping doesn't allow players to gather high client velocity
        double minimizedOffset = Math.min(offset, 1);

        // Normalize offsets
        player.uncertaintyHandler.lastHorizontalOffset = minimizedOffset;
        player.uncertaintyHandler.lastVerticalOffset = minimizedOffset;
    }

    private void removeOffsetLenience() {
        player.uncertaintyHandler.lastHorizontalOffset = 0;
        player.uncertaintyHandler.lastVerticalOffset = 0;
    }

    @Override
    public void onReload(ConfigManager config) {
        setbackDecayMultiplier = config.getDoubleElse("Simulation.setback-decay-multiplier", 0.999);
        threshold = config.getDoubleElse("Simulation.threshold", 0.001);
        sneakThreshold = config.getDoubleElse("Simulation.sneak-threshold", 0.015);
        carpetThreshold = config.getDoubleElse("Simulation.carpet-threshold", 0.02);
        bedThreshold = config.getDoubleElse("Simulation.bed-threshold", 0.035);
        // water-threshold was declared and used below but NEVER read here, so it
        // was always 0.0 — the water tolerance (Math.max with waterThreshold) did
        // nothing, which is why in-water Simulation false flags kept being
        // reported. Now actually loaded from config.
        waterThreshold = config.getDoubleElse("Simulation.water-threshold", 0.04);
        pingThresholdBaseline = config.getIntElse("Simulation.ping-threshold-baseline-ms", 60);
        pingThresholdPerMs = config.getDoubleElse("Simulation.ping-threshold-per-ms", 0.00003);
        immediateSetbackThreshold = config.getDoubleElse("Simulation.immediate-setback-threshold", 0.1);
        maxAdvantage = config.getDoubleElse("Simulation.max-advantage", 1);
        maxCeiling = config.getDoubleElse("Simulation.max-ceiling", 4);
        setbackViolationThreshold = config.getDoubleElse("Simulation.setback-violation-threshold", 1);
        flightToggleGraceMs = config.getIntElse("Simulation.flight-toggle-grace-ms", 1000);
        if (maxAdvantage == -1) maxAdvantage = Double.MAX_VALUE;
        if (immediateSetbackThreshold == -1) immediateSetbackThreshold = Double.MAX_VALUE;
    }

    public boolean doesOffsetFlag(double offset) {
        return offset >= threshold;
    }
}
