package ac.jester.anticheat.hooks;

import ac.jester.anticheat.hooks.impl.*;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public final class HookManager {
    private static final List<PluginHook> activeHooks = new ArrayList<>();

    private static GSitHook gsitHook;
    private static DeluxeCombatHook deluxeCombatHook;
    private static AuraSkillsHook auraSkillsHook;
    private static WorldGuardHook worldGuardHook;
    private static ItemsAdderHook itemsAdderHook;
    private static LPXHook lpxHook;

    public static void init() {
        register(gsitHook = new GSitHook());
        register(deluxeCombatHook = new DeluxeCombatHook());
        register(auraSkillsHook = new AuraSkillsHook());
        register(worldGuardHook = new WorldGuardHook());
        register(itemsAdderHook = new ItemsAdderHook());
        register(lpxHook = new LPXHook());

        ExemptionProvider.register(new BukkitExemptionProvider(gsitHook, worldGuardHook, auraSkillsHook, itemsAdderHook, deluxeCombatHook));

        if (activeHooks.isEmpty()) {
            LogUtil.info("No compatibility hooks active (no supported plugins installed).");
        } else {
            StringBuilder sb = new StringBuilder();
            for (PluginHook h : activeHooks) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(h.getPluginName());
            }
            LogUtil.info("Active compatibility hooks (" + activeHooks.size() + "): " + sb);
        }
    }

    private static void register(PluginHook hook) {
        if (!Bukkit.getPluginManager().isPluginEnabled(hook.getPluginName())) {
            return;
        }
        try {
            hook.onEnable();
            if (hook.isAvailable()) {
                activeHooks.add(hook);
                LogUtil.info("Hooked into " + hook.getPluginName());
            } else {
                LogUtil.warn(hook.getPluginName() + " is installed but its hook failed to "
                        + "initialize (likely an unsupported version). Integration is INACTIVE.");
            }
        } catch (Throwable e) {
            LogUtil.warn("Failed to hook into " + hook.getPluginName() + ": " + e);
        }
    }

    public static void reload() {
        activeHooks.forEach(PluginHook::onReload);
    }

    public static void disable() {
        activeHooks.forEach(PluginHook::onDisable);
        activeHooks.clear();
    }

    public static GSitHook getGSit() { return gsitHook; }
    public static DeluxeCombatHook getDeluxeCombat() { return deluxeCombatHook; }
    public static AuraSkillsHook getAuraSkills() { return auraSkillsHook; }
    public static WorldGuardHook getWorldGuard() { return worldGuardHook; }
    public static ItemsAdderHook getItemsAdder() { return itemsAdderHook; }
    public static LPXHook getLPX() { return lpxHook; }
}
