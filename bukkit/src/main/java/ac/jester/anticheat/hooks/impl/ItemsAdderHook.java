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

/**
 * Hooks into ItemsAdder to handle custom blocks via reflection.
 * No compile-time dependency on ItemsAdder API — resolved at runtime only.
 *
 * ItemsAdder's public API (confirmed against the official 4.0.10 javadoc — see
 * https://lonedev6.github.io/API-ItemsAdder/dev/lone/itemsadder/api/CustomBlock.html)
 * does NOT expose block hardness or break-speed at all, so we can't read a custom
 * block's configured hardness directly. Instead, FastBreak/Nuker are told a
 * custom block was JUST broken (via {@link #hasRecentCustomBreak}, fed by
 * CustomBlockBreakEvent) and soften their vanilla-hardness-based timing
 * assumptions for that ~300ms window, since those assumptions don't apply to
 * a block whose real break timing is config-defined by ItemsAdder.
 */
public final class ItemsAdderHook implements PluginHook {

    private boolean available = false;

    // Cached reflection handle
    private Method byAlreadyPlaced;

    // UUID -> expiry. Players breaking custom blocks in this window get softened FastBreak checks.
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

        // isCustomBlock — the core (and only) capability this class needs from CustomBlock.
        try {
            byAlreadyPlaced = customBlockClass.getMethod("byAlreadyPlaced", Block.class);
            available = true;
        } catch (NoSuchMethodException e) {
            LogUtil.warn("ItemsAdder hook: CustomBlock.byAlreadyPlaced(Block) not found ("
                    + e.getMessage() + "). isCustomBlock() will be inactive.");
            return;
        }

        // Register listener for CustomBlockBreakEvent via reflection so we track recent breaks
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

    /** Returns true if the block at this location is a custom ItemsAdder block. */
    public boolean isCustomBlock(Block block) {
        if (!available || block == null) return false;
        try {
            return byAlreadyPlaced.invoke(null, block) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** True if this player recently broke a custom block — soften FastBreak for this window. */
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
