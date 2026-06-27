package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.hooks.PluginHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrazyEnchantments compatibility (tested target: CrazyEnchantments 2.7.2).
 *
 * CrazyEnchantments' custom enchants (Haste, Blast, ...) are NOT carried in the
 * packets the anticheat sees, so the mining checks (FastBreak, NukerA) can't
 * account for them and false-flag. This hook reads them from the held item's
 * LORE on the main/region thread (where Bukkit access is safe) and caches them
 * for the packet-thread checks to query through ExemptionProvider.
 *
 * Lore-based ON PURPOSE: it works across CrazyEnchantments versions without
 * locking to its internal API, and the enchant names are configurable so they
 * can be matched to your CrazyEnchantments config / language. Updates are
 * event-driven (Folia-safe): when a player holds/swaps an item, or on join.
 */
public final class CrazyEnchantmentsHook implements PluginHook, Listener {

    private boolean available;

    // UUID -> [mining-speed level, hasAreaBreak ? 1 : 0]
    private final Map<UUID, int[]> cache = new ConcurrentHashMap<>();

    private Set<String> miningNames = Set.of("haste");
    private Set<String> areaNames = Set.of("blast");

    @Override
    public String getPluginName() {
        return "CrazyEnchantments";
    }

    @Override
    public void onEnable() {
        reloadNames();
        Plugin self = Bukkit.getPluginManager().getPlugin("JesterAntiCheat");
        if (self == null) return;
        Bukkit.getPluginManager().registerEvents(this, self);
        // Seed any players already online (e.g. on /reload).
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p.getUniqueId(), p.getInventory().getItemInMainHand());
        }
        available = true;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void onReload() {
        reloadNames();
    }

    @Override
    public void onDisable() {
        cache.clear();
        available = false;
    }

    private void reloadNames() {
        var cfg = GrimAPI.INSTANCE.getConfigManager().getConfig();
        miningNames = lower(cfg.getStringListElse("compatibility.crazyenchantments.mining-enchants", List.of("Haste")));
        areaNames = lower(cfg.getStringListElse("compatibility.crazyenchantments.area-break-enchants", List.of("Blast")));
    }

    // ── event-driven cache updates (run on the correct thread) ────────────────
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        update(event.getPlayer().getUniqueId(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // getMainHandItem() = what ends up in the main hand after the swap.
        update(event.getPlayer().getUniqueId(), event.getMainHandItem());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer().getUniqueId(), event.getPlayer().getInventory().getItemInMainHand());
    }

    private void update(UUID uuid, ItemStack item) {
        int mining = 0;
        boolean area = false;
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            if (lore != null) {
                for (String raw : lore) {
                    String line = ChatColor.stripColor(raw).trim().toLowerCase(Locale.ROOT);
                    for (String name : miningNames) {
                        if (line.equals(name) || line.startsWith(name + " ")) {
                            mining = Math.max(mining, parseLevel(line.substring(name.length()).trim()));
                        }
                    }
                    for (String name : areaNames) {
                        if (line.equals(name) || line.startsWith(name + " ")) area = true;
                    }
                }
            }
        }
        if (mining > 0 || area) {
            cache.put(uuid, new int[]{mining, area ? 1 : 0});
        } else {
            cache.remove(uuid);
        }
    }

    // ── queried from the packet thread ────────────────────────────────────────
    public int getMiningLevel(UUID uuid) {
        int[] v = cache.get(uuid);
        return v == null ? 0 : v[0];
    }

    public boolean hasAreaBreak(UUID uuid) {
        int[] v = cache.get(uuid);
        return v != null && v[1] == 1;
    }

    private static Set<String> lower(List<String> in) {
        Set<String> out = new HashSet<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    private static int parseLevel(String s) {
        if (s.isEmpty()) return 1;
        String tok = s.split("\\s+")[0];
        try {
            return Integer.parseInt(tok);
        } catch (NumberFormatException ignored) {
        }
        int r = romanToInt(tok.toUpperCase(Locale.ROOT));
        return r > 0 ? r : 1;
    }

    private static int romanToInt(String s) {
        Map<Character, Integer> m = Map.of('I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            Integer val = m.get(s.charAt(i));
            if (val == null) return 0;
            if (val < prev) total -= val;
            else {
                total += val;
                prev = val;
            }
        }
        return total;
    }
}
