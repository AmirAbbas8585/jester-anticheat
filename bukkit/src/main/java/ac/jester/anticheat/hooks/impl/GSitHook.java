package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GSitHook implements PluginHook, Listener {
    private boolean available = false;

    private final Set<String> sitting = ConcurrentHashMap.newKeySet();
    private int taskId = -1;

    private final List<Method> stateMethods = new ArrayList<>();

    @Override
    public String getPluginName() { return "GSit"; }

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("GSit")) return;
        available = true;

        discoverApi();

        Plugin self = Bukkit.getPluginManager().getPlugin("JesterAntiCheat");
        if (self == null) return;

        registerMountEvent(self, "org.bukkit.event.entity.EntityMountEvent");
        registerMountEvent(self, "org.spigotmc.event.entity.EntityMountEvent");

        taskId = Bukkit.getScheduler().runTaskTimer(self, this::refresh, 1L, 1L).getTaskId();
    }

    private void discoverApi() {
        Class<?> api = null;
        for (String name : new String[]{
                "dev.geco.gsit.api.GSitAPI",
                "dev.geco.gsit.GSitAPI",
                "me.geco.gsit.api.GSitAPI"}) {
            try {
                api = Class.forName(name);
                break;
            } catch (Throwable ignored) {
            }
        }
        if (api == null) {
            LogUtil.info("GSit: API class not found; relying on mount events + entity poll.");
            return;
        }

        for (Method m : api.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || !params[0].isAssignableFrom(Player.class)) continue;
            String n = m.getName();
            if (!(n.startsWith("is") || n.startsWith("has"))) continue;
            String low = n.toLowerCase();
            if (low.contains("sit") || low.contains("crawl") || low.contains("lay") || low.contains("pose")) {
                m.setAccessible(true);
                stateMethods.add(m);
            }
        }

        if (stateMethods.isEmpty()) {
            LogUtil.info("GSit: found API but no state-query methods matched; using mount events + poll.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Method m : stateMethods) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(m.getName());
            }
            LogUtil.info("GSit: using API state queries [" + sb + "] + mount events + poll.");
        }
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        sitting.clear();
    }

    @SuppressWarnings("unchecked")
    private void registerMountEvent(Plugin self, String className) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
            Method getEntity = eventClass.getMethod("getEntity");
            Method getMount = eventClass.getMethod("getMount");
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR,
                    (listener, event) -> {
                        try {
                            Object rider = getEntity.invoke(event);
                            Object mount = getMount.invoke(event);
                            if (rider instanceof Player p && mount instanceof Entity vehicle
                                    && !isVanillaVehicle(vehicle.getType().name())) {
                                sitting.add(p.getName().toLowerCase());
                            }
                        } catch (Throwable ignored) {}
                    }, self, true);
        } catch (Throwable ignored) {
        }
    }

    private void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                String key = player.getName().toLowerCase();
                if (computeSitting(player)) {
                    sitting.add(key);
                } else {
                    sitting.remove(key);
                }
            } catch (Throwable ignored) {
            }
        }
        if (!sitting.isEmpty()) {
            sitting.removeIf(name -> Bukkit.getPlayerExact(name) == null);
        }
    }

    private boolean computeSitting(Player player) {
        for (Method m : stateMethods) {
            try {
                if (Boolean.TRUE.equals(m.invoke(null, player))) return true;
            } catch (Throwable ignored) {
            }
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null && !isVanillaVehicle(vehicle.getType().name())) {
            return true;
        }
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Player) return true;
        }
        return false;
    }

    private boolean isVanillaVehicle(String typeName) {
        if (typeName.contains("BOAT") || typeName.contains("MINECART")) return true;
        switch (typeName) {
            case "HORSE":
            case "DONKEY":
            case "MULE":
            case "SKELETON_HORSE":
            case "ZOMBIE_HORSE":
            case "PIG":
            case "STRIDER":
            case "CAMEL":
            case "LLAMA":
            case "TRADER_LLAMA":
            case "HAPPY_GHAST":
                return true;
            default:
                return false;
        }
    }

    public boolean isSitting(Player player) {
        return player != null && isSittingByName(player.getName());
    }

    public boolean isSittingByName(String name) {
        return available && name != null && sitting.contains(name.toLowerCase());
    }

    public boolean isAvailable() { return available; }
}
