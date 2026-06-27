package ac.jester.anticheat.hooks;

import ac.jester.anticheat.player.GrimPlayer;

/**
 * Platform-agnostic hook exemption queries.
 * Bukkit registers a concrete implementation at startup via ExemptionProvider.register().
 * Checks call ExemptionProvider.get() which is safe to call from any thread.
 */
public abstract class ExemptionProvider {

    private static volatile ExemptionProvider instance;

    public static void register(ExemptionProvider provider) {
        instance = provider;
    }

    public static ExemptionProvider get() {
        return instance;
    }

    /** True if the player is sitting/crawling/laying via GSit — exempt movement checks. */
    public abstract boolean isSitting(GrimPlayer player);

    /** True if WorldGuard allows flight at the player's location. */
    public abstract boolean canFly(GrimPlayer player);

    /** Speed multiplier from AuraSkills (1.0 = vanilla). */
    public abstract double getSpeedMultiplier(GrimPlayer player);

    /** Jump height multiplier from AuraSkills (1.0 = vanilla). */
    public abstract double getJumpMultiplier(GrimPlayer player);

    /** True if the player is in a WorldGuard PvP-disabled region. */
    public abstract boolean isPvpDisabled(GrimPlayer player);

    /**
     * True if the player broke an ItemsAdder custom block in the last ~300ms.
     * Custom blocks can have hardness/break timing that doesn't match vanilla
     * assumptions — FastBreak softens its inter-dig delay penalty during this
     * window instead of judging custom blocks by vanilla pacing.
     */
    public abstract boolean hasRecentCustomBlockBreak(GrimPlayer player);

    /**
     * True if the player took combat damage (vanilla or plugin-applied, e.g.
     * DeluxeCombat's custom knockback) in the last ~500ms. The general movement
     * predictor (MovementA) can mispredict for a tick or two right after a
     * non-trivial velocity change — not an exploit, just a hard-to-predict
     * physics moment.
     */
    public abstract boolean hasRecentCombatKnockback(GrimPlayer player);

    /** No-op implementation used when no platform registers one. */
    public static final ExemptionProvider NOOP = new ExemptionProvider() {
        @Override public boolean isSitting(GrimPlayer p) { return false; }
        @Override public boolean canFly(GrimPlayer p) { return false; }
        @Override public double getSpeedMultiplier(GrimPlayer p) { return 1.0; }
        @Override public double getJumpMultiplier(GrimPlayer p) { return 1.0; }
        @Override public boolean isPvpDisabled(GrimPlayer p) { return false; }
        @Override public boolean hasRecentCustomBlockBreak(GrimPlayer p) { return false; }
        @Override public boolean hasRecentCombatKnockback(GrimPlayer p) { return false; }
    };

    /** Convenience: returns instance or NOOP if none registered. */
    public static ExemptionProvider safe() {
        ExemptionProvider p = instance;
        return p != null ? p : NOOP;
    }
}
