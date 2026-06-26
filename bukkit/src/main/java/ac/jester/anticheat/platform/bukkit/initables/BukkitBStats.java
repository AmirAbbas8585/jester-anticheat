package ac.jester.anticheat.platform.bukkit.initables;

import ac.jester.anticheat.manager.init.start.StartableInitable;
import ac.jester.anticheat.platform.bukkit.SkyAntiCheatPlugin;
import ac.jester.anticheat.utils.anticheat.Constants;
import io.github.retrooper.packetevents.bstats.bukkit.Metrics;

public class BukkitBStats implements StartableInitable {
    @Override
    public void start() {
        try {
            new Metrics(SkyAntiCheatPlugin.LOADER, Constants.BSTATS_PLUGIN_ID);
        } catch (Exception ignored) {}
    }
}
