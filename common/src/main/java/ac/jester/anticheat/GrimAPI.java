package ac.jester.anticheat;

import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.internal.plugin.resolver.GrimExtensionManager;
import ac.grim.grimac.internal.event.OptimizedEventBus;
import ac.jester.anticheat.manager.AlertManagerImpl;
import ac.jester.anticheat.manager.AlertRateLimiter;
import ac.jester.anticheat.manager.FreezeManager;
import ac.jester.anticheat.manager.InitManager;
import ac.jester.anticheat.manager.JesterCheckConfig;
import ac.jester.anticheat.manager.SpectateManager;
import ac.jester.anticheat.manager.TickManager;
import ac.jester.anticheat.manager.config.BaseConfigManager;
import ac.jester.anticheat.manager.init.Initable;
import ac.jester.anticheat.manager.violationdatabase.ViolationDatabaseManager;
import ac.jester.anticheat.platform.api.Platform;
import ac.jester.anticheat.platform.api.PlatformLoader;
import ac.jester.anticheat.platform.api.PlatformServer;
import ac.jester.anticheat.platform.api.command.CommandService;
import ac.jester.anticheat.platform.api.manager.ItemResetHandler;
import ac.jester.anticheat.platform.api.manager.MessagePlaceHolderManager;
import ac.jester.anticheat.platform.api.manager.PermissionRegistrationManager;
import ac.jester.anticheat.platform.api.manager.PlatformPluginManager;
import ac.jester.anticheat.platform.api.player.PlatformPlayerFactory;
import ac.jester.anticheat.platform.api.scheduler.PlatformScheduler;
import ac.jester.anticheat.platform.api.sender.SenderFactory;
import ac.jester.anticheat.utils.anticheat.PlayerDataManager;
import ac.jester.anticheat.utils.common.arguments.CommonGrimArguments;
import ac.jester.anticheat.utils.reflection.ReflectionUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;


@Getter
public final class GrimAPI {
    public static final GrimAPI INSTANCE = new GrimAPI();

    @Getter
    private final Platform platform = detectPlatform();
    private final BaseConfigManager configManager;
    private final AlertManagerImpl alertManager;
    private final AlertRateLimiter alertRateLimiter;
    private final SpectateManager spectateManager;
    private final FreezeManager freezeManager;
    private final PlayerDataManager playerDataManager;
    private final TickManager tickManager;
    private final GrimExtensionManager extensionManager;
    private final EventBus eventBus;
    private final GrimExternalAPI externalAPI;
    private ViolationDatabaseManager violationDatabaseManager;
    private PlatformLoader loader;
    @Getter
    private InitManager initManager;
    private boolean initialized = false;

    private GrimAPI() {
        this.configManager = new BaseConfigManager();
        this.alertManager = new AlertManagerImpl();
        this.alertRateLimiter = new AlertRateLimiter();
        this.spectateManager = new SpectateManager();
        this.freezeManager = new FreezeManager();
        this.playerDataManager = new PlayerDataManager();
        this.tickManager = new TickManager();
        this.extensionManager = new GrimExtensionManager();
        this.eventBus = new OptimizedEventBus(extensionManager);
        this.externalAPI = new GrimExternalAPI(this);
    }

    // the order matters
    private static Platform detectPlatform() {
        Platform override = CommonGrimArguments.PLATFORM_OVERRIDE.value();
        if (override != null) return override;
        if (ReflectionUtils.hasClass("io.papermc.paper.threadedregions.RegionizedServer")) return Platform.FOLIA;
        if (ReflectionUtils.hasClass("org.bukkit.Bukkit")) return Platform.BUKKIT;
        if (ReflectionUtils.hasClass("net.fabricmc.loader.api.FabricLoader")) return Platform.FABRIC;
        throw new IllegalStateException("Unknown platform!");
    }

    public void load(PlatformLoader platformLoader, Initable... platformSpecificInitables) {
        this.loader = platformLoader;
        this.violationDatabaseManager = new ViolationDatabaseManager(getGrimPlugin());
        this.initManager = new InitManager(loader.getPacketEvents(), platformSpecificInitables);
        this.initManager.load();
        this.initialized = true;
    }

    public void start() {
        checkInitialized();
        // Load JesterAC per-check config and database
        JesterCheckConfig.reloadGlobals(configManager.getConfig());
        ac.jester.anticheat.database.DatabaseManager.init(configManager.getConfig());
        initManager.start();
    }

    public void stop() {
        checkInitialized();
        ac.jester.anticheat.database.DatabaseManager.shutdown();
        initManager.stop();
    }

    public PlatformScheduler getScheduler() {
        return loader.getScheduler();
    }

    public PlatformPlayerFactory getPlatformPlayerFactory() {
        return loader.getPlatformPlayerFactory();
    }

    public GrimPlugin getGrimPlugin() {
        return loader.getPlugin();
    }

    public SenderFactory<?> getSenderFactory() {
        return loader.getSenderFactory();
    }

    public ItemResetHandler getItemResetHandler() {
        return loader.getItemResetHandler();
    }

    public PlatformPluginManager getPluginManager() {
        return loader.getPluginManager();
    }

    public PlatformServer getPlatformServer() {
        return loader.getPlatformServer();
    }

    public @NotNull MessagePlaceHolderManager getMessagePlaceHolderManager() {
        return loader.getMessagePlaceHolderManager();
    }

    public CommandService getCommandService() {
        return loader.getCommandService();
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GrimAPI has not been initialized!");
        }
    }

    public PermissionRegistrationManager getPermissionManager() {
        return loader.getPermissionManager();
    }

    public GrimExtensionManager getExtensionManager() {
        return extensionManager;
    }
}
