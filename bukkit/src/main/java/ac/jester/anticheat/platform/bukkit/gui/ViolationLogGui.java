package ac.jester.anticheat.platform.bukkit.gui;

import ac.jester.anticheat.database.DatabaseManager;
import ac.jester.anticheat.platform.bukkit.SkyAntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * In-game violation log viewer — /skyac logs <player>.
 *
 * Reads from the SkyAC database (SQLite/MySQL), so it works for offline
 * players and survives restarts. One paper per violation, newest first,
 * 45 per page with arrow navigation.
 */
public final class ViolationLogGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** Marks our inventories and carries paging state for the click handler. */
    private static final class LogHolder implements InventoryHolder {
        final String target;
        final int page;
        Inventory inventory;

        LogHolder(String target, int page) {
            this.target = target;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(UUID viewerUuid, String targetName, int page) {
        if (!DatabaseManager.isEnabled()) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null) viewer.sendMessage("§cDatabase is disabled — no logs available.");
            return;
        }

        // Fetch one extra row to know whether a next page exists
        DatabaseManager.fetchViolations(targetName, PAGE_SIZE + 1, page * PAGE_SIZE).thenAccept(entries ->
                Bukkit.getScheduler().runTask(SkyAntiCheatPlugin.LOADER, () -> {
                    Player viewer = Bukkit.getPlayer(viewerUuid);
                    if (viewer == null) return;

                    boolean hasNext = entries.size() > PAGE_SIZE;
                    List<DatabaseManager.ViolationEntry> pageEntries =
                            hasNext ? entries.subList(0, PAGE_SIZE) : entries;

                    LogHolder holder = new LogHolder(targetName, page);
                    Inventory inv = Bukkit.createInventory(holder, 54,
                            "§6ᴀᴄ §8» §fLogs: §e" + targetName + " §8(page " + (page + 1) + ")");
                    holder.inventory = inv;

                    int slot = 0;
                    for (DatabaseManager.ViolationEntry entry : pageEntries) {
                        inv.setItem(slot++, buildEntryItem(entry));
                    }

                    if (pageEntries.isEmpty()) {
                        inv.setItem(22, named(Material.BARRIER, "§cNo logged violations",
                                List.of("§7Nothing recorded for §e" + targetName)));
                    }

                    if (page > 0) {
                        inv.setItem(45, named(Material.ARROW, "§ePrevious page", List.of()));
                    }
                    if (hasNext) {
                        inv.setItem(53, named(Material.ARROW, "§eNext page", List.of()));
                    }

                    viewer.openInventory(inv);
                }));
    }

    private static ItemStack buildEntryItem(DatabaseManager.ViolationEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Violations: §6x" + (int) entry.vl);
        lore.add("§7Ping: §6" + entry.ping + "ms §7TPS: §6" + String.format("%.1f", entry.tps));
        lore.add("§7Time: §f" + DATE.format(new Date(entry.flaggedAt)));
        if (entry.verbose != null && !entry.verbose.isEmpty()) {
            lore.add("");
            // wrap long verbose strings to keep the tooltip readable
            String verbose = entry.verbose;
            for (int i = 0; i < verbose.length() && i < 120; i += 40) {
                lore.add("§8" + verbose.substring(i, Math.min(verbose.length(), i + 40)));
            }
        }
        return named(Material.PAPER, "§e" + entry.checkName, lore);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LogHolder holder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() != event.getInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.ARROW) return;

        int slot = event.getSlot();
        if (slot == 45 && holder.page > 0) {
            open(event.getWhoClicked().getUniqueId(), holder.target, holder.page - 1);
        } else if (slot == 53) {
            open(event.getWhoClicked().getUniqueId(), holder.target, holder.page + 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LogHolder) {
            event.setCancelled(true);
        }
    }
}
