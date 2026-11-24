package ac.jester.anticheat.hooks.impl;

import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.hooks.PluginHook;
import ac.jester.anticheat.platform.api.entity.GrimEntity;
import ac.jester.anticheat.platform.bukkit.JesterAntiCheatPlugin;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CheckHacksHook implements PluginHook {
    private boolean available = false;

    private Plugin checkHacksPlugin;
    private Method getConfigManager;
    private Method getCheckManager;

    private Method isDetectFlagEnabled;
    private Method isGrimEnabled;
    private Method getFlagCheckHacks;

    private Method canAutoCheck;
    private Method isChecking;
    private Method startCheck;

    private Method messageParse;
    private Method messageBroadcastAlerts;

    @Override
    public String getPluginName() {
        return "CheckHacks";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void onEnable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CheckHacks");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        try {
            Class<?> pluginClass = plugin.getClass();
            getConfigManager = pluginClass.getMethod("getConfigManager");
            getCheckManager = pluginClass.getMethod("getCheckManager");

            Object configManager = getConfigManager.invoke(plugin);
            Object checkManager = getCheckManager.invoke(plugin);
            if (configManager == null || checkManager == null) {
                return;
            }

            Class<?> configClass = configManager.getClass();
            isDetectFlagEnabled = configClass.getMethod("isDetectFlagEnabled");
            getFlagCheckHacks = configClass.getMethod("getFlagCheckHacks");
            try {
                isGrimEnabled = configClass.getMethod("isGrimEnabled");
            } catch (NoSuchMethodException ignored) {
                isGrimEnabled = null;
            }

            Class<?> checkMgrClass = checkManager.getClass();
            canAutoCheck = checkMgrClass.getMethod("canAutoCheck", UUID.class);
            isChecking = checkMgrClass.getMethod("isChecking", UUID.class);
            startCheck = checkMgrClass.getMethod(
                    "startCheck", Player.class, Player.class, List.class, boolean.class, String.class);

            try {
                Class<?> messageUtil = Class.forName(
                        "me.branduzzo.checkHacks.utils.MessageUtil", true, pluginClass.getClassLoader());
                messageParse = messageUtil.getMethod("parse", pluginClass, String.class, Map.class);
                messageBroadcastAlerts = messageUtil.getMethod(
                        "broadcastAlerts", pluginClass, messageParse.getReturnType());
            } catch (Throwable ignored) {
                messageParse = null;
                messageBroadcastAlerts = null;
            }

            this.checkHacksPlugin = plugin;

            EventBus eventBus = GrimAPI.INSTANCE.getExternalAPI().getEventBus();
            GrimPlugin context = GrimAPI.INSTANCE.getGrimPlugin();
            eventBus.subscribe(context, FlagEvent.class, this::onFlag);

            available = true;
        } catch (Throwable e) {
            LogUtil.warn("CheckHacks detected but failed to bind API methods: " + e.getMessage());
            available = false;
        }
    }

    @Override
    public void onDisable() {
        available = false;
        checkHacksPlugin = null;
    }

    private void onFlag(FlagEvent event) {
        if (!available || event.isCancelled()) return;
        Plugin plugin = this.checkHacksPlugin;
        if (plugin == null || !plugin.isEnabled()) return;

        UUID uuid = event.getUser().getUniqueId();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        try {
            Object configManager = getConfigManager.invoke(plugin);
            if (configManager == null) return;

            if (!Boolean.TRUE.equals(isDetectFlagEnabled.invoke(configManager))) return;
            if (isGrimEnabled != null && !Boolean.TRUE.equals(isGrimEnabled.invoke(configManager))) return;

            Object checkManager = getCheckManager.invoke(plugin);
            if (checkManager == null) return;

            if (!Boolean.TRUE.equals(canAutoCheck.invoke(checkManager, uuid))) return;
            if (Boolean.TRUE.equals(isChecking.invoke(checkManager, uuid))) return;

            List<?> hacks = (List<?>) getFlagCheckHacks.invoke(configManager);
            if (hacks == null || hacks.isEmpty()) return;

            GrimEntity grimEntity = JesterAntiCheatPlugin.LOADER.getPlatformPlayerFactory().getFromUUID(uuid);
            if (grimEntity == null) return;

            GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    grimEntity,
                    GrimAPI.INSTANCE.getGrimPlugin(),
                    () -> {
                        if (!player.isOnline() || !plugin.isEnabled()) return;
                        try {
                            if (!Boolean.TRUE.equals(canAutoCheck.invoke(checkManager, uuid))) return;
                            if (Boolean.TRUE.equals(isChecking.invoke(checkManager, uuid))) return;

                            if (messageParse != null && messageBroadcastAlerts != null) {
                                try {
                                    Object msg = messageParse.invoke(
                                            null, plugin, "anticheat-trigger", Map.of("player", player.getName()));
                                    if (msg != null) {
                                        messageBroadcastAlerts.invoke(null, plugin, msg);
                                    }
                                } catch (Throwable ignored) {
                                }
                            }

                            startCheck.invoke(checkManager, player, null, hacks, true, "Anticheat flag: Jester");
                        } catch (Throwable t) {
                            LogUtil.warn("CheckHacks auto-flag check failed for " + player.getName() + ": " + t.getMessage());
                        }
                    },
                    null,
                    0
            );
        } catch (Throwable t) {
            LogUtil.warn("CheckHacks hook error: " + t.getMessage());
        }
    }
}
