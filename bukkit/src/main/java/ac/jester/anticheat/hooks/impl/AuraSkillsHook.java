package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class AuraSkillsHook implements PluginHook {
    private boolean available = false;

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

            Class<?> statsClass = Class.forName("dev.aurelium.auraskills.api.stat.Stats");
            speedStat = statsClass.getField("SPEED").get(null);
            jumpStat = statsClass.getField("JUMP_HEIGHT").get(null);

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
        }
    }

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
