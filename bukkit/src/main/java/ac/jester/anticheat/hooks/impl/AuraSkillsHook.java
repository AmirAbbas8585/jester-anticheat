package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hooks into AuraSkills to account for skill-based stat boosts.
 * Players with high Speed/Jump stats can move faster/jump higher legitimately.
 * Movement checks need to know the effective bonus to avoid false flags.
 * Uses reflection so no compile-time AuraSkills dependency is needed.
 */
public final class AuraSkillsHook implements PluginHook {

    private boolean available = false;

    // Cached API instance and reflected method handles
    private Object api;
    private Method getUser;
    private Method getStatLevel;
    private Object speedStat;
    private Object jumpStat;

    @Override
    public String getPluginName() { return "AuraSkills"; }

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) return;
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Method getMethod = apiClass.getMethod("get");
            api = getMethod.invoke(null);
            if (api == null) return;

            getUser = apiClass.getMethod("getUser", UUID.class);

            // Stats.SPEED and Stats.JUMP_HEIGHT are static fields on the Stats enum class
            Class<?> statsClass = Class.forName("dev.aurelium.auraskills.api.stat.Stats");
            speedStat = statsClass.getField("SPEED").get(null);
            jumpStat = statsClass.getField("JUMP_HEIGHT").get(null);

            // getStatLevel(Stat) on SkillsUser — find the single-arg method by name
            Class<?> skillsUserClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            for (Method m : skillsUserClass.getMethods()) {
                if (m.getName().equals("getStatLevel") && m.getParameterCount() == 1) {
                    getStatLevel = m;
                    break;
                }
            }

            if (getStatLevel != null) {
                available = true;
            }
        } catch (Exception e) {
            // AuraSkills API unavailable or changed — gracefully disabled
        }
    }

    /**
     * Returns the player's effective speed multiplier from AuraSkills stats.
     * 1.0 = vanilla, 1.2 = 20% bonus.
     */
    public double getSpeedMultiplier(Player player) {
        if (!available || player == null) return 1.0;
        try {
            Object user = getUser.invoke(api, player.getUniqueId());
            if (user == null) return 1.0;
            Object result = getStatLevel.invoke(user, speedStat);
            double speedBonus = result instanceof Number ? ((Number) result).doubleValue() : 0.0;
            return 1.0 + (speedBonus / 100.0);
        } catch (Exception e) {
            return 1.0;
        }
    }

    /** Returns the player's effective jump height multiplier from AuraSkills stats. */
    public double getJumpMultiplier(Player player) {
        if (!available || player == null) return 1.0;
        try {
            Object user = getUser.invoke(api, player.getUniqueId());
            if (user == null) return 1.0;
            Object result = getStatLevel.invoke(user, jumpStat);
            double jumpBonus = result instanceof Number ? ((Number) result).doubleValue() : 0.0;
            return 1.0 + (jumpBonus / 100.0);
        } catch (Exception e) {
            return 1.0;
        }
    }

    public boolean isAvailable() { return available; }
}
