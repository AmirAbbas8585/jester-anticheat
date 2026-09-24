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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager implements Listener {
    private static final class State {
        volatile long lastActiveMs;
        volatile boolean warned;
        double ax, ay, az;
        String world;
    }

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    private boolean enabled;
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
            LogUtil.info("AFK system disabled (afk.enabled: false).");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
        } catch (Throwable t) {
            LogUtil.warn("AFK system could not schedule its task (Folia?). Region AFK enforcement is off.");
            return;
        }
        LogUtil.info("AFK system active: no-AFK zones set via the WorldGuard flag '"
                + WorldGuardHook.AFK_FLAG_NAME + "', limit " + (maxAfkMs / 1000)
                + "s, warn " + (warnBeforeMs / 1000) + "s before.");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        states.clear();
    }

    private void loadConfig() {
        var cfg = ac.jester.anticheat.GrimAPI.INSTANCE.getConfigManager().getConfig();
        enabled = cfg.getBooleanElse("afk.enabled", true);
        maxAfkMs = Math.max(10, cfg.getLongElse("afk.max-afk-seconds", 900)) * 1000L;
        warnBeforeMs = Math.max(0, cfg.getLongElse("afk.warn-before-seconds", 60)) * 1000L;
        double dist = cfg.getDoubleElse("afk.min-move-distance", 2.5);
        minMoveDistSq = dist * dist;
        bypassPerm = cfg.getStringElse("afk.bypass-permission", "jester.afk.bypass");
        warnMsg = ChatColor.translateAlternateColorCodes('&',
                cfg.getStringElse("afk.messages.warn",
                        "&e[AFK] &fYou will be kicked in &c%seconds%s&f for being AFK here. Move to stay."));
        kickMsg = ChatColor.translateAlternateColorCodes('&',
                cfg.getStringElse("afk.messages.kick",
                        "&c[AFK]\n&fYou were kicked for being AFK too long in a no-AFK area."))
                .replace("\\n", "\n");
    }

    public void reload() {
        boolean wasRunning = taskId != -1;
        loadConfig();
        if (!enabled && wasRunning) {
            stop();
        } else if (enabled && !wasRunning) {
            start();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        State s = states.computeIfAbsent(e.getPlayer().getUniqueId(), k -> freshState(to));
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
        if (s != null) touch(s);
    }

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

    private void tick() {
        if (!enabled) return;
        WorldGuardHook wg = HookManager.getWorldGuard();
        if (wg == null || !wg.isAvailable() || !wg.isAfkFlagAvailable()) return;

        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            State s = states.computeIfAbsent(p.getUniqueId(), k -> freshState(p.getLocation()));

            if (bypassPerm != null && !bypassPerm.isEmpty() && p.hasPermission(bypassPerm)) {
                s.lastActiveMs = now;
                s.warned = false;
                continue;
            }

            boolean inZone = wg.isAfkBlocked(p.getLocation());
            if (!inZone) {
                s.lastActiveMs = now;
                s.warned = false;
                continue;
            }

            long idle = now - s.lastActiveMs;
            if (idle >= maxAfkMs) {
                p.kickPlayer(kickMsg);
                states.remove(p.getUniqueId());
            } else if (!s.warned && warnBeforeMs > 0 && idle >= (maxAfkMs - warnBeforeMs)) {
                long secs = (maxAfkMs - idle + 999) / 1000;
                p.sendMessage(warnMsg.replace("%seconds%", String.valueOf(secs)));
                s.warned = true;
            }
        }
    }
}
