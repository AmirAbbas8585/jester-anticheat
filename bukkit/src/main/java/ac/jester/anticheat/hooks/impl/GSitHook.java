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

/**
 * Hooks into GSit to detect sitting / crawling / laying / player-sit (sitting on
 * another player's head). Affected players have a modified position, pose, and
 * ground state, so movement, timer, and packet checks must exempt them.
 *
 * Detection uses three layers because no single one is reliable across GSit
 * versions and seat modes (real-entity vs packet-only seats):
 *   1. AUTO-DISCOVERED GSit API state queries — scans GSitAPI for any static
 *      boolean "is" / "has" (Player) method named sit/crawl/lay/pose. This works
 *      even for packet-only seats (no server entity) and survives renames.
 *   2. Mount events — instant detection for real-entity seats.
 *   3. A main-thread poll — backup via getVehicle()/passengers.
 *
 * The hot path (isSitting) only READS a concurrent set; never touches Bukkit.
 */
public final class GSitHook implements PluginHook, Listener {

    private boolean available = false;

    // Keyed by lowercase NAME, not UUID: in offline/proxy setups the Bukkit
    // UUID and the UUID grim derives from the connection can differ, which made
    // the set lookup always miss. Names are consistent across both.
    private final Set<String> sitting = ConcurrentHashMap.newKeySet();
    private int taskId = -1;

    // Auto-discovered GSitAPI state-query methods
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

        // Instant mount detection (event class moved packages across versions)
        registerMountEvent(self, "org.bukkit.event.entity.EntityMountEvent");
        registerMountEvent(self, "org.spigotmc.event.entity.EntityMountEvent");

        // Per-tick poll backup
        taskId = Bukkit.getScheduler().runTaskTimer(self, this::refresh, 1L, 1L).getTaskId();
    }

    /** Find GSit's API class and auto-discover its sitting/crawling/etc. queries. */
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
            // Only state QUERIES (is*/has*), not actions like removeSit/kickSit
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
                            // Only a NON-vanilla mount (a seat) counts; boats/horses
                            // must stay checkable (BoatFly etc.)
                            if (rider instanceof Player p && mount instanceof Entity vehicle
                                    && !isVanillaVehicle(vehicle.getType().name())) {
                                sitting.add(p.getName().toLowerCase());
                            }
                        } catch (Throwable ignored) {}
                    }, self, true);
        } catch (Throwable ignored) {
            // Event class absent on this version — other variant / poll covers it
        }
    }

    /** Main-thread: recompute every online player's sitting state. */
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

    /** Main-thread only: API queries + entity inspection. */
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

    /** Hot path — thread-safe set read, no Bukkit access. */
    public boolean isSitting(Player player) {
        return player != null && isSittingByName(player.getName());
    }

    /** Name-keyed lookup — robust to offline/proxy UUID differences. */
    public boolean isSittingByName(String name) {
        return available && name != null && sitting.contains(name.toLowerCase());
    }

    public boolean isAvailable() { return available; }
}
