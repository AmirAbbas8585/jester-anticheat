package ac.jester.anticheat.hooks;

import ac.jester.anticheat.player.GrimPlayer;

public abstract class ExemptionProvider {
    private static volatile ExemptionProvider instance;

    public static void register(ExemptionProvider provider) {
        instance = provider;
    }

    public static ExemptionProvider get() {
        return instance;
    }

    public abstract boolean isSitting(GrimPlayer player);

    public abstract boolean canFly(GrimPlayer player);

    public abstract double getSpeedMultiplier(GrimPlayer player);

    public abstract double getJumpMultiplier(GrimPlayer player);

    public abstract boolean isPvpDisabled(GrimPlayer player);

    public abstract boolean hasRecentCustomBlockBreak(GrimPlayer player);

    public abstract boolean hasRecentCombatKnockback(GrimPlayer player);

    public static final ExemptionProvider NOOP = new ExemptionProvider() {
        @Override public boolean isSitting(GrimPlayer p) { return false; }
        @Override public boolean canFly(GrimPlayer p) { return false; }
        @Override public double getSpeedMultiplier(GrimPlayer p) { return 1.0; }
        @Override public double getJumpMultiplier(GrimPlayer p) { return 1.0; }
        @Override public boolean isPvpDisabled(GrimPlayer p) { return false; }
        @Override public boolean hasRecentCustomBlockBreak(GrimPlayer p) { return false; }
        @Override public boolean hasRecentCombatKnockback(GrimPlayer p) { return false; }
    };

    public static ExemptionProvider safe() {
        ExemptionProvider p = instance;
        return p != null ? p : NOOP;
    }
}
