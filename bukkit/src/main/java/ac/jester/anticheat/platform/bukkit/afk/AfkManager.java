package ac.jester.anticheat.platform.bukkit.afk;

import ac.jester.anticheat.hooks.HookManager;
import ac.jester.anticheat.hooks.impl.WorldGuardHook;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Region-scoped AFK enforcement.
 *
 * Inside the configured WorldGuard regions a player may idle for at most
 * {@code max-afk-seconds}; one minute before that they get a warning, and at the
 * limit they are kicked. Everywhere ELSE AFK is unlimited.
 *
 * Anti-AFK-macro resistance: only activity a macro cannot cheaply fake counts as
 * "active" — real horizontal displacement past a radius, breaking/placing blocks,
 * moving inventory items, dropping items, or chatting. Things a macro spams to
 * look busy do NOT count: camera rotation, jumping in place, swing/click packets,
 * COMMANDS (a macro could spam one), and tiny back-and-forth jitter under the
 * radius. A player truly playing resets constantly; an AFK-farm macro never does.
 */
public final class AfkManager implements Listener {

    private static final class State {
        volatile long lastActiveMs;
        volatile boolean warned;
        // Anchor: position at last counted displacement, to measure NET movement
        double ax, ay, az;
        String world;
    }

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    private boolean enabled;
    private final Set<String> regions = new HashSet<>();
    private long maxAfkMs;
    private long warnBeforeMs;
    private double minMoveDistSq;
    private String bypassPerm;
    private String warnMsg;
    private String kickMsg;
    private int taskId = -1;

    public AfkManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loadConfig();
        if (!enabled) {
            LogUtil.info("AFK system disabled in afk.yml.");
            return;
        }
        if (regions.isEmpty()) {
            LogUtil.info("AFK system on, but no no-AFK regions configured in afk.yml — nothing will be enforced.");
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Poll once per second on the main thread (WorldGuard region lookups and
        // kicks must be main-thread). On Folia the legacy scheduler is unsupported,
        // but WorldGuard isn't Folia-compatible anyway, so AFK enforcement is moot
        // there — fail gracefully instead of breaking plugin enable.
        try {
            taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
        } catch (Throwable t) {
            LogUtil.warn("AFK system could not schedule its task (Folia?). Region AFK enforcement is off.");
            return;
        }
        LogUtil.info("AFK system active: " + regions.size() + " no-AFK region(s), limit "
                + (maxAfkMs / 1000) + "s, warn " + (warnBeforeMs / 1000) + "s before.");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        states.clear();
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "afk.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            writeDefault(file);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("enabled", true);
        regions.clear();
        for (String r : cfg.getStringList("regions")) regions.add(r.toLowerCase());
        maxAfkMs = Math.max(10, cfg.getLong("max-afk-seconds", 900)) * 1000L;
        warnBeforeMs = Math.max(0, cfg.getLong("warn-before-seconds", 60)) * 1000L;
        double dist = cfg.getDouble("min-move-distance", 2.5);
        minMoveDistSq = dist * dist;
        bypassPerm = cfg.getString("bypass-permission", "skyac.afk.bypass");
        warnMsg = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("messages.warn",
                        "&e[AFK] &fYou will be kicked in &c%seconds%s&f for being AFK here. Move to stay."));
        kickMsg = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("messages.kick",
                        "&c[AFK]\n&fYou were kicked for being AFK too long in a no-AFK area."));
    }

    private void writeDefault(File file) {
        String def = String.join("\n",
                "# Jester Anti Cheat — region-scoped AFK enforcement.",
                "# Players may idle freely EXCEPT inside the WorldGuard regions listed below.",
                "enabled: true",
                "",
                "# WorldGuard region ids (lowercase) where AFK is NOT allowed.",
                "# Requires WorldGuard installed. Leave empty to enforce nowhere.",
                "regions:",
                "  - example_farm",
                "  - grinder",
                "",
                "# How long a player may stay AFK inside those regions before being kicked.",
                "max-afk-seconds: 900   # 15 minutes",
                "",
                "# Warn the player this many seconds before the kick.",
                "warn-before-seconds: 60",
                "",
                "# Net horizontal distance (blocks) a player must travel from where they",
                "# stopped to count as active. Defeats jump-in-place / tiny walk macros that",
                "# stay on one farming spot. Bigger = stricter. 2.5 is a good default.",
                "min-move-distance: 2.5",
                "",
                "# Players with this permission are never AFK-kicked.",
                "bypass-permission: skyac.afk.bypass",
                "",
                "messages:",
                "  warn: \"&e[AFK] &fYou will be kicked in &c%seconds%s&f for being AFK here. Move to stay.\"",
                "  kick: \"&c[AFK]\\n&fYou were kicked for being AFK too long in a no-AFK area.\"",
                "");
        try (java.io.FileWriter w = new java.io.FileWriter(file)) {
            w.write(def);
        } catch (Exception e) {
            LogUtil.warn("Could not write default afk.yml: " + e);
        }
    }

    /** Call from the plugin to re-read afk.yml. */
    public void reload() {
        boolean wasRunning = taskId != -1;
        loadConfig();
        if (!enabled && wasRunning) {
            stop();
        } else if (enabled && !wasRunning) {
            start();
        }
    }

    // ── Activity signals that a macro cannot cheaply fake ─────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // Ignore pure look/rotation changes — anti-AFK macros spin the camera.
        if (e.getTo() == null) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        State s = states.computeIfAbsent(e.getPlayer().getUniqueId(), k -> freshState(to));
        // Net HORIZONTAL displacement from the anchor (ignore Y → jumping in place
        // doesn't count). Only past the radius is it "real" movement.
        if (!to.getWorld().getName().equals(s.world)) {
            anchorTo(s, to);
            touch(s);
            return;
        }
        double dx = to.getX() - s.ax, dz = to.getZ() - s.az;
        if (dx * dx + dz * dz >= minMoveDistSq) {
            anchorTo(s, to);
            touch(s);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { active(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { active(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { active(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) active(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { active(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        State s = states.get(e.getPlayer().getUniqueId());
        if (s != null) touch(s); // async — timestamp only, no Bukkit access
    }

    // NOTE: commands deliberately do NOT count as activity — a macro could keep
    // itself "active" by spamming a command. Chat counts; commands don't.

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { states.remove(e.getPlayer().getUniqueId()); }

    private void active(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> freshState(p.getLocation()));
        anchorTo(s, p.getLocation());
        touch(s);
    }

    private void touch(State s) {
        s.lastActiveMs = System.currentTimeMillis();
        s.warned = false;
    }

    private void anchorTo(State s, Location loc) {
        s.ax = loc.getX();
        s.ay = loc.getY();
        s.az = loc.getZ();
        s.world = loc.getWorld() != null ? loc.getWorld().getName() : null;
    }

    private State freshState(Location loc) {
        State s = new State();
        s.lastActiveMs = System.currentTimeMillis();
        anchorTo(s, loc);
        return s;
    }

    // ── Enforcement ───────────────────────────────────────────────────────────

    private void tick() {
        if (!enabled || regions.isEmpty()) return;
        WorldGuardHook wg = HookManager.getWorldGuard();
        if (wg == null || !wg.isAvailable()) return; // can't resolve regions

        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            State s = states.computeIfAbsent(p.getUniqueId(), k -> freshState(p.getLocation()));

            if (bypassPerm != null && !bypassPerm.isEmpty() && p.hasPermission(bypassPerm)) {
                s.lastActiveMs = now;
                s.warned = false;
                continue;
            }

            boolean inZone = wg.isInAnyRegion(p.getLocation(), regions);
            if (!inZone) {
                // Outside a no-AFK region: keep the timer fresh so entering one
                // starts a new 15-minute window.
                s.lastActiveMs = now;
                s.warned = false;
                continue;
            }

            long idle = now - s.lastActiveMs;
            if (idle >= maxAfkMs) {
                p.kickPlayer(kickMsg);
                states.remove(p.getUniqueId());
            } else if (!s.warned && warnBeforeMs > 0 && idle >= (maxAfkMs - warnBeforeMs)) {
                long secs = (maxAfkMs - idle + 999) / 1000; // round up
                p.sendMessage(warnMsg.replace("%seconds%", String.valueOf(secs)));
                s.warned = true;
            }
        }
    }
}
