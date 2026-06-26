package ac.jester.anticheat.platform.api;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.jester.anticheat.platform.api.command.CommandService;
import ac.jester.anticheat.platform.api.manager.ItemResetHandler;
import ac.jester.anticheat.platform.api.manager.MessagePlaceHolderManager;
import ac.jester.anticheat.platform.api.manager.PermissionRegistrationManager;
import ac.jester.anticheat.platform.api.manager.PlatformPluginManager;
import ac.jester.anticheat.platform.api.player.PlatformPlayerFactory;
import ac.jester.anticheat.platform.api.scheduler.PlatformScheduler;
import ac.jester.anticheat.platform.api.sender.SenderFactory;
import com.github.retrooper.packetevents.PacketEventsAPI;
import org.jetbrains.annotations.NotNull;

public interface PlatformLoader {
    PlatformScheduler getScheduler();

    PlatformPlayerFactory getPlatformPlayerFactory();

    PacketEventsAPI<?> getPacketEvents();

    ItemResetHandler getItemResetHandler();

    CommandService getCommandService();

    SenderFactory<?> getSenderFactory();

    GrimPlugin getPlugin();

    PlatformPluginManager getPluginManager();

    PlatformServer getPlatformServer();

    // Intended for use for platform specific service/API bringup
    // Method will be called when InitManager.load() is called
    void registerAPIService();

    // Used to replace text placeholders in messages
    // Currently only supports PlaceHolderAPI on Bukkit
    @NotNull
    MessagePlaceHolderManager getMessagePlaceHolderManager();

    PermissionRegistrationManager getPermissionManager();
}
