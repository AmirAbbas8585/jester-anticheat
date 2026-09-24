package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WorldGuardHook implements PluginHook {
    private boolean available = false;

    private Object regionContainer;
    private Method createQuery;
    private Method adaptLocation;
    private Method adaptPlayer;
    private Method adaptWorld;
    private Method rcGet;
    private Method blockVectorAt;
    private Method getApplicableRegions;
    private Method getRegionId;

    private Method testState;
    private Object flightFlag;
    private Object pvpFlag;
    private Class<?> flagArrayClass;

    public static final String AFK_FLAG_NAME = "jester-afk-blocked";
    private static Object registeredAfkFlag;

    @Override
    public String getPluginName() { return "WorldGuard"; }

    public static void registerCustomFlags() {
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object registry = wgClass.getMethod("getFlagRegistry").invoke(wgInstance);
            Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flag = stateFlagClass.getConstructor(String.class, boolean.class)
                    .newInstance(AFK_FLAG_NAME, false);
            try {
                registry.getClass().getMethod("register", flagClass).invoke(registry, flag);
                registeredAfkFlag = flag;
                LogUtil.info("Registered WorldGuard flag '" + AFK_FLAG_NAME
                        + "' — mark no-AFK regions with: /rg flag <region> " + AFK_FLAG_NAME + " allow");
            } catch (Exception alreadyRegistered) {
                registeredAfkFlag = registry.getClass().getMethod("get", String.class)
                        .invoke(registry, AFK_FLAG_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Method getRC = findMethod(platform.getClass(), "getRegionContainer");
            if (getRC == null) return;
            regionContainer = getRC.invoke(platform);

            createQuery = findMethod(regionContainer.getClass(), "createQuery");
            if (createQuery == null) return;

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptPlayer = adapterClass.getMethod("adapt", Player.class);
            adaptLocation = adapterClass.getMethod("adapt", Location.class);
            adaptWorld = adapterClass.getMethod("adapt", org.bukkit.World.class);

            rcGet = findGet(regionContainer.getClass());

            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVectorAt = bv3.getMethod("at", int.class, int.class, int.class);

            getRegionId = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion")
                    .getMethod("getId");

            available = true;

            try {
                Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                try { flightFlag = flagsClass.getField("FLIGHT").get(null); } catch (Throwable ignored) {}
                try { pvpFlag = flagsClass.getField("PVP").get(null); } catch (Throwable ignored) {}

                Class<?> locationClass = Class.forName("com.sk89q.worldedit.util.Location");
                Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer");
                Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
                flagArrayClass = Array.newInstance(flagClass, 0).getClass();
                Object sampleQuery = createQuery.invoke(regionContainer);
                testState = findTestState(sampleQuery.getClass(), locationClass, localPlayerClass, flagArrayClass);
            } catch (Throwable ignored) {
            }
        } catch (Throwable e) {
            LogUtil.warn("WorldGuard hook could not initialize: " + e
                    + " — region-based AFK zones and /fly region exemption are off.");
        }
    }

    public Set<String> getRegionIds(Location location) {
        if (!available || location == null || location.getWorld() == null) return Collections.emptySet();
        try {
            Object weWorld = adaptWorld.invoke(null, location.getWorld());
            Object manager = rcGet.invoke(regionContainer, weWorld);
            if (manager == null) return Collections.emptySet();

            if (getApplicableRegions == null) {
                getApplicableRegions = findGetApplicableRegions(manager.getClass());
                if (getApplicableRegions == null) return Collections.emptySet();
            }

            Object vec = blockVectorAt.invoke(null,
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object set = getApplicableRegions.invoke(manager, vec);
            if (!(set instanceof Iterable)) return Collections.emptySet();

            Set<String> ids = new HashSet<>();
            for (Object region : (Iterable<?>) set) {
                Object id = getRegionId.invoke(region);
                if (id != null) ids.add(id.toString().toLowerCase());
            }
            return ids;
        } catch (Throwable e) {
            return Collections.emptySet();
        }
    }

    public boolean isInAnyRegion(Location location, Set<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return false;
        Set<String> here = getRegionIds(location);
        if (here.isEmpty()) return false;
        for (String want : regionIds) {
            if (here.contains(want.toLowerCase())) return true;
        }
        return false;
    }

    public boolean canFly(Player player) {
        if (!available || testState == null || flightFlag == null || player == null) return false;
        try {
            Object query = createQuery.invoke(regionContainer);
            Object localPlayer = adaptPlayer.invoke(null, player);
            Object wgLocation = adaptLocation.invoke(null, player.getLocation());
            Object flagArr = Array.newInstance(flagArrayClass.getComponentType(), 1);
            Array.set(flagArr, 0, flightFlag);
            return Boolean.TRUE.equals(testState.invoke(query, wgLocation, localPlayer, flagArr));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPvpDisabled(Location location) {
        if (!available || testState == null || pvpFlag == null || location == null) return false;
        try {
            Object query = createQuery.invoke(regionContainer);
            Object wgLocation = adaptLocation.invoke(null, location);
            Object flagArr = Array.newInstance(flagArrayClass.getComponentType(), 1);
            Array.set(flagArr, 0, pvpFlag);
            return !Boolean.TRUE.equals(testState.invoke(query, wgLocation, null, flagArr));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAfkBlocked(Location location) {
        if (!available || testState == null || registeredAfkFlag == null || location == null) return false;
        try {
            Object query = createQuery.invoke(regionContainer);
            Object wgLocation = adaptLocation.invoke(null, location);
            Object flagArr = Array.newInstance(flagArrayClass.getComponentType(), 1);
            Array.set(flagArr, 0, registeredAfkFlag);
            return Boolean.TRUE.equals(testState.invoke(query, wgLocation, null, flagArr));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAfkFlagAvailable() { return registeredAfkFlag != null; }

    public boolean isAvailable() { return available; }

    private static Method findMethod(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            for (Method m : iface.getMethods()) {
                if (m.getName().equals(name)) return m;
            }
        }
        return null;
    }

    private static Method findGet(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().endsWith("world.World")) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Method findGetApplicableRegions(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("getApplicableRegions") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().endsWith("BlockVector3")) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Method findTestState(Class<?> clazz, Class<?> locClass, Class<?> lpClass, Class<?> flagArrClass) {
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equals("testState")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 3 && params[0].isAssignableFrom(locClass) && params[2].isArray()) {
                return m;
            }
        }
        return null;
    }
}
