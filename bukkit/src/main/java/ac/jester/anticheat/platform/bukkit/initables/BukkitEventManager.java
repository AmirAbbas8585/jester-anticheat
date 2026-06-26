package ac.jester.anticheat.platform.bukkit.initables;

import ac.jester.anticheat.manager.init.start.StartableInitable;
import ac.jester.anticheat.platform.bukkit.SkyAntiCheatPlugin;
import ac.jester.anticheat.platform.bukkit.events.PistonEvent;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;

public class BukkitEventManager implements StartableInitable {
    public void start() {
        LogUtil.info("Registering singular bukkit event... (PistonEvent)");

        Bukkit.getPluginManager().registerEvents(new PistonEvent(), SkyAntiCheatPlugin.LOADER);
    }
}
