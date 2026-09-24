package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemsAdderHook implements PluginHook {
    private boolean available = false;

    private Method byAlreadyPlaced;

    private final ConcurrentHashMap<UUID, Long> recentCustomBreak = new ConcurrentHashMap<>();
    private static final long CUSTOM_BREAK_WINDOW_MS = 300L;

    @Override
    public String getPluginName() { return "ItemsAdder"; }

    @Override
    public void onEnable() {
        Plugin self = Bukkit.getPluginManager().getPlugin("JesterAntiCheat");
        if (self == null) return;

        Class<?> customBlockClass;
        try {
            customBlockClass = Class.forName("dev.lone.itemsadder.api.CustomBlock");
        } catch (ClassNotFoundException e) {
            LogUtil.warn("ItemsAdder hook: CustomBlock API class not found (" + e.getMessage()
                    + "). This ItemsAdder version/build may not expose the expected API.");
            return;
        }

        try {
            byAlreadyPlaced = customBlockClass.getMethod("byAlreadyPlaced", Block.class);
            available = true;
        } catch (NoSuchMethodException e) {
            LogUtil.warn("ItemsAdder hook: CustomBlock.byAlreadyPlaced(Block) not found ("
                    + e.getMessage() + "). isCustomBlock() will be inactive.");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> breakEventClass = (Class<? extends Event>)
                    Class.forName("dev.lone.itemsadder.api.Events.CustomBlockBreakEvent");
            Method getPlayerMethod = breakEventClass.getMethod("getPlayer");

            EventExecutor executor = (listener, event) -> {
                try {
                    Player p = (Player) getPlayerMethod.invoke(event);
                    if (p != null) {
                        recentCustomBreak.put(p.getUniqueId(),
                                System.currentTimeMillis() + CUSTOM_BREAK_WINDOW_MS);
                    }
                } catch (Exception ignored) {}
            };

            Bukkit.getPluginManager().registerEvent(
                    breakEventClass, new Listener() {}, EventPriority.MONITOR, executor, self, true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LogUtil.warn("ItemsAdder hook: CustomBlockBreakEvent not found (" + e.getMessage()
                    + ") — recent-custom-break tracking is inactive; isCustomBlock still works.");
        }
    }

    public boolean isCustomBlock(Block block) {
        if (!available || block == null) return false;
        try {
            return byAlreadyPlaced.invoke(null, block) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasRecentCustomBreak(UUID playerId) {
        Long expiry = recentCustomBreak.get(playerId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            recentCustomBreak.remove(playerId);
            return false;
        }
        return true;
    }

    public boolean isAvailable() { return available; }
}
