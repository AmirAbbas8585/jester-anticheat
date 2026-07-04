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

/**
 * Hooks into WorldGuard via reflection (no compile-time dependency).
 *
 * Provides two unrelated capabilities, each bound best-effort so a missing piece
 * never disables the rest:
 *   - region NAME membership ({@link #getRegionIds}) — used by the AFK system to
 *     restrict no-AFK zones to specific regions. This only needs the core region
 *     container, so it works on every WorldGuard build.
 *   - region FLAG queries ({@link #canFly}, {@link #isPvpDisabled}) — optional;
 *     the FLIGHT flag does not exist on stock WorldGuard, so that one is simply
 *     left unbound (canFly returns false) instead of failing the whole hook.
 */
public final class WorldGuardHook implements PluginHook {

    private boolean available = false;

    // Core reflection handles (region container + adapters)
    private Object regionContainer;
    private Method createQuery;
    private Method adaptLocation;
    private Method adaptPlayer;
    private Method adaptWorld;
    private Method rcGet;                 // RegionContainer.get(World) -> RegionManager
    private Method blockVectorAt;         // BlockVector3.at(int,int,int)
    private Method getApplicableRegions;  // RegionManager.getApplicableRegions(BlockVector3)
    private Method getRegionId;           // ProtectedRegion.getId()

    // Optional flag handles
    private Method testState;
    private Object flightFlag;
    private Object pvpFlag;
    private Class<?> flagArrayClass;

    /** Custom StateFlag registered in the plugin's onLoad(). Static because the
     *  hook instance doesn't exist yet at onLoad time. */
    public static final String AFK_FLAG_NAME = "jester-afk-blocked";
    private static Object registeredAfkFlag;

    @Override
    public String getPluginName() { return "WorldGuard"; }

    /**
     * Registers Jester's custom WorldGuard flags. MUST be called from the
     * plugin's onLoad() — WorldGuard only accepts new flags before it finishes
     * enabling. Silent no-op if WorldGuard isn't installed or its API changed.
     *
     * Admins then mark a no-AFK region with:
     *   /rg flag &lt;region&gt; jester-afk-blocked allow
     */
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
                // A /reload re-runs onLoad — reuse the flag WorldGuard already holds.
                registeredAfkFlag = registry.getClass().getMethod("get", String.class)
                        .invoke(registry, AFK_FLAG_NAME);
            }
        } catch (Throwable ignored) {
            // WorldGuard absent or API changed — the AFK flag is simply unavailable.
        }
    }

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return;
        try {
            // WorldGuard.getInstance().getPlatform().getRegionContainer()
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Method getRC = findMethod(platform.getClass(), "getRegionContainer");
            if (getRC == null) return;
            regionContainer = getRC.invoke(platform);

            createQuery = findMethod(regionContainer.getClass(), "createQuery");
            if (createQuery == null) return;

            // BukkitAdapter.adapt(...) overloads
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptPlayer = adapterClass.getMethod("adapt", Player.class);
            adaptLocation = adapterClass.getMethod("adapt", Location.class);
            adaptWorld = adapterClass.getMethod("adapt", org.bukkit.World.class);

            // RegionContainer.get(com.sk89q.worldedit.world.World) -> RegionManager
            rcGet = findGet(regionContainer.getClass());

            // BlockVector3.at(int,int,int)
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVectorAt = bv3.getMethod("at", int.class, int.class, int.class);

            // RegionManager.getApplicableRegions(BlockVector3) — resolved lazily from
            // the manager instance (its concrete class differs across versions); see
            // getRegionIds. ProtectedRegion.getId():
            getRegionId = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion")
                    .getMethod("getId");

            // The region-name path is enough to call this hook "available".
            available = true;

            // ── Optional flag queries (best-effort, never abort) ──────────────
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
                // Flag queries unavailable on this build — region names still work.
            }
        } catch (Throwable e) {
            LogUtil.warn("WorldGuard hook could not initialize: " + e
                    + " — region-based AFK zones and /fly region exemption are off.");
        }
    }

    /**
     * Returns the lowercase ids of every WorldGuard region containing this
     * location, or an empty set if WorldGuard is unavailable / the world is
     * unmanaged. Used by the AFK system to detect no-AFK zones.
     */
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

    /** True if the player's current location is inside any of the given region ids. */
    public boolean isInAnyRegion(Location location, Set<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return false;
        Set<String> here = getRegionIds(location);
        if (here.isEmpty()) return false;
        for (String want : regionIds) {
            if (here.contains(want.toLowerCase())) return true;
        }
        return false;
    }

    /** Returns true if the player's location allows flight via the FLIGHT region flag. */
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

    /** Returns true if PvP is disabled at this location via the PVP region flag. */
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

    /**
     * True if AFK is blocked at this location — i.e. the player is inside a region
     * whose {@code jester-afk-blocked} flag is set to {@code allow}. Replaces the
     * old afk.yml region-name list with native WorldGuard flag control.
     */
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

    /** True if the AFK flag was successfully registered (WorldGuard present). */
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

    /** RegionContainer.get(World) — the single-arg overload taking a WorldEdit World. */
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

    /** RegionManager.getApplicableRegions(BlockVector3) — not the ProtectedRegion overload. */
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
