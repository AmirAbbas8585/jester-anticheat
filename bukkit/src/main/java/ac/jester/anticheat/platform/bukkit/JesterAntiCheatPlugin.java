package ac.jester.anticheat.platform.bukkit;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.GrimExternalAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.jester.anticheat.command.CloudCommandService;
import ac.grim.grimac.internal.platform.bukkit.resolver.BukkitResolverRegistrar;
import ac.jester.anticheat.manager.init.Initable;
import ac.jester.anticheat.manager.init.start.ExemptOnlinePlayersOnReload;
import ac.jester.anticheat.manager.init.start.StartableInitable;
import ac.jester.anticheat.platform.api.Platform;
import ac.jester.anticheat.platform.api.PlatformLoader;
import ac.jester.anticheat.platform.api.PlatformServer;
import ac.jester.anticheat.platform.api.command.CommandService;
import ac.jester.anticheat.platform.api.manager.ItemResetHandler;
import ac.jester.anticheat.platform.api.manager.MessagePlaceHolderManager;
import ac.jester.anticheat.platform.api.manager.PlatformPluginManager;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.player.PlatformPlayerFactory;
import ac.jester.anticheat.platform.api.scheduler.PlatformScheduler;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.platform.api.sender.SenderFactory;
import ac.jester.anticheat.platform.bukkit.initables.BukkitBStats;
import ac.jester.anticheat.platform.bukkit.initables.BukkitEventManager;
import ac.jester.anticheat.platform.bukkit.initables.BukkitTickEndEvent;
import ac.jester.anticheat.platform.bukkit.manager.BukkitItemResetHandler;
import ac.jester.anticheat.platform.bukkit.manager.BukkitMessagePlaceHolderManager;
import ac.jester.anticheat.platform.bukkit.manager.BukkitParserDescriptorFactory;
import ac.jester.anticheat.platform.bukkit.manager.BukkitPermissionRegistrationManager;
import ac.jester.anticheat.platform.bukkit.manager.BukkitPlatformPluginManager;
import ac.jester.anticheat.platform.bukkit.player.BukkitPlatformPlayerFactory;
import ac.jester.anticheat.platform.bukkit.scheduler.bukkit.BukkitPlatformScheduler;
import ac.jester.anticheat.platform.bukkit.scheduler.folia.FoliaPlatformScheduler;
import ac.jester.anticheat.platform.bukkit.sender.BukkitSenderFactory;
import ac.jester.anticheat.platform.bukkit.utils.placeholder.PlaceholderAPIExpansion;
import ac.jester.anticheat.hooks.HookManager;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import ac.jester.anticheat.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;


public final class JesterAntiCheatPlugin extends JavaPlugin implements PlatformLoader {

    public static JesterAntiCheatPlugin LOADER;

    private final LazyHolder<PlatformScheduler> scheduler = LazyHolder.simple(this::createScheduler);
    private final LazyHolder<PacketEventsAPI<?>> packetEvents = LazyHolder.simple(() -> SpigotPacketEventsBuilder.build(this));
    private final LazyHolder<BukkitSenderFactory> senderFactory = LazyHolder.simple(BukkitSenderFactory::new);
    private final LazyHolder<ItemResetHandler> itemResetHandler = LazyHolder.simple(BukkitItemResetHandler::new);
    private final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);
    private final CloudCommandAdapter commandAdapter = new BukkitParserDescriptorFactory();

    @Getter private final PlatformPlayerFactory platformPlayerFactory = new BukkitPlatformPlayerFactory();
    @Getter private final PlatformPluginManager pluginManager = new BukkitPlatformPluginManager();
    @Getter private final GrimPlugin plugin;
    @Getter private final PlatformServer platformServer = new BukkitPlatformServer();
    @Getter private final MessagePlaceHolderManager messagePlaceHolderManager = new BukkitMessagePlaceHolderManager();
    @Getter private final BukkitPermissionRegistrationManager permissionManager = new BukkitPermissionRegistrationManager();

    public JesterAntiCheatPlugin() {
        BukkitResolverRegistrar registrar = new BukkitResolverRegistrar();
        registrar.registerAll(GrimAPI.INSTANCE.getExtensionManager());
        this.plugin = registrar.resolvePlugin(this);
    }

    @Override
    public void onLoad() {
        LOADER = this;
        GrimAPI.INSTANCE.load(this, this.getBukkitInitTasks());
    }

    private Initable[] getBukkitInitTasks() {
        return new Initable[] {
                new ExemptOnlinePlayersOnReload(),
                new BukkitEventManager(),
                new BukkitTickEndEvent(),
                new BukkitBStats(),
                (StartableInitable) () -> {
                    if (BukkitMessagePlaceHolderManager.hasPlaceholderAPI) {
                        new PlaceholderAPIExpansion().register();
                    }
                }
        };
    }

    private ac.jester.anticheat.platform.bukkit.afk.AfkManager afkManager;
    private ac.jester.anticheat.platform.bukkit.update.UpdateChecker updateChecker;
    private boolean started = false;

    @Override
    public void onEnable() {
        // Startup-only remote kill switch. Runs before anything else and is
        // fail-closed: if the version is killed/too old, OR the gate server is
        // unreachable, the plugin refuses to enable. (Hardcoded endpoint.)
        if (ac.jester.anticheat.platform.bukkit.update.VersionGate.isBlocked(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GrimAPI.INSTANCE.start();
        started = true;
        HookManager.init();
        Bukkit.getPluginManager().registerEvents(
                new ac.jester.anticheat.platform.bukkit.listeners.FreezeListener(), this);

        // Region-scoped AFK enforcement (own-server build only). This feature is
        // stripped from the obfuscated public jar, so its class may be absent —
        // tolerate that and run without it instead of crashing on enable.
        try {
            afkManager = new ac.jester.anticheat.platform.bukkit.afk.AfkManager(this);
            afkManager.start();
        } catch (Throwable absentOrFailed) {
            afkManager = null;
        }

        // Update notifier (own + obf builds). Off unless update-checker.url is set.
        updateChecker = new ac.jester.anticheat.platform.bukkit.update.UpdateChecker(this);
        updateChecker.start();

        // Violation log GUI (/jester logs <player>)
        ac.jester.anticheat.platform.bukkit.gui.ViolationLogGui logGui =
                new ac.jester.anticheat.platform.bukkit.gui.ViolationLogGui();
        Bukkit.getPluginManager().registerEvents(logGui, this);
        ac.jester.anticheat.hooks.GuiProvider.register(new ac.jester.anticheat.hooks.GuiProvider() {
            @Override
            public boolean openViolationLog(java.util.UUID viewerUuid, String targetName) {
                if (!ac.jester.anticheat.database.DatabaseManager.isEnabled()) return false;
                ac.jester.anticheat.platform.bukkit.gui.ViolationLogGui.open(viewerUuid, targetName, 0);
                return true;
            }
        });
    }

    @Override
    public void onDisable() {
        if (afkManager != null) afkManager.stop();
        if (updateChecker != null) updateChecker.stop();
        // If the version gate refused enable, nothing below was initialised.
        if (!started) return;
        HookManager.disable();
        GrimAPI.INSTANCE.stop();
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler.get();
    }

    @Override
    public PacketEventsAPI<?> getPacketEvents() {
        return packetEvents.get();
    }

    @Override
    public ItemResetHandler getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public SenderFactory<CommandSender> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    public void registerAPIService() {
        final GrimExternalAPI externalAPI = GrimAPI.INSTANCE.getExternalAPI();
        final EventBus eventBus = externalAPI.getEventBus();
        final ac.grim.grimac.api.plugin.GrimPlugin context = GrimAPI.INSTANCE.getGrimPlugin();

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.GrimJoinEvent.class, (event) -> {
            ac.grim.grimac.api.events.GrimJoinEvent bukkitEvent =
                    new ac.grim.grimac.api.events.GrimJoinEvent(event.getUser());

            Bukkit.getPluginManager().callEvent(bukkitEvent);
        });

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.GrimQuitEvent.class, (event) -> {
            ac.grim.grimac.api.events.GrimQuitEvent bukkitEvent =
                    new ac.grim.grimac.api.events.GrimQuitEvent(event.getUser());

            Bukkit.getPluginManager().callEvent(bukkitEvent);
        });

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.GrimReloadEvent.class, (event) -> {
            ac.grim.grimac.api.events.GrimReloadEvent bukkitEvent =
                    new ac.grim.grimac.api.events.GrimReloadEvent(event.isSuccess());

            Bukkit.getPluginManager().callEvent(bukkitEvent);

            // /jester reload only reloads Grim's own config — afk.yml is a separate
            // file, so re-read it here too on a successful reload.
            if (event.isSuccess() && afkManager != null) {
                GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler()
                        .run(GrimAPI.INSTANCE.getGrimPlugin(), afkManager::reload);
            }
        });

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.FlagEvent.class, (event) -> {
            ac.grim.grimac.api.events.FlagEvent bukkitEvent =
                    new ac.grim.grimac.api.events.FlagEvent(
                            event.getUser(),
                            event.getCheck(),
                            event.getVerbose()
                    );

            Bukkit.getPluginManager().callEvent(bukkitEvent);

            if (bukkitEvent.isCancelled()) {
                event.setCancelled(true);
            }
        });

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.CommandExecuteEvent.class, (event) -> {
            ac.grim.grimac.api.events.CommandExecuteEvent bukkitEvent =
                    new ac.grim.grimac.api.events.CommandExecuteEvent(
                            event.getUser(),
                            event.getCheck(),
                            event.getVerbose(),
                            event.getCommand()
                    );

            Bukkit.getPluginManager().callEvent(bukkitEvent);

            if (bukkitEvent.isCancelled()) {
                event.setCancelled(true);
            }
        });

        eventBus.subscribe(context, ac.grim.grimac.api.event.events.CompletePredictionEvent.class, (event) -> {
            // Note: New event doesn't have verbose, passing null or check name is standard fallback
            ac.grim.grimac.api.events.CompletePredictionEvent bukkitEvent =
                    new ac.grim.grimac.api.events.CompletePredictionEvent(
                            event.getUser(),
                            event.getCheck(),
                            "",
                            event.getOffset()
                    );

            Bukkit.getPluginManager().callEvent(bukkitEvent);

            if (bukkitEvent.isCancelled()) {
                event.setCancelled(true);
            }
        });

        GrimAPIProvider.init(externalAPI);
        Bukkit.getServicesManager().register(GrimAbstractAPI.class, externalAPI, JesterAntiCheatPlugin.LOADER, ServicePriority.Normal);
    }

    private PlatformScheduler createScheduler() {
        return GrimAPI.INSTANCE.getPlatform() == Platform.FOLIA ? new FoliaPlatformScheduler() : new BukkitPlatformScheduler();
    }

    private CommandService createCommandService() {
        try {
            return new CloudCommandService(this::createCloudCommandManager, commandAdapter);
        } catch (Throwable t) {
            LogUtil.warn("CRITICAL: Failed to initialize Command Framework. " +
                    "Grim will continue to run with no commands.", t);
            return () -> {};
        }
    }

    private CommandManager<Sender> createCloudCommandManager() {
        LegacyPaperCommandManager<Sender> manager = new LegacyPaperCommandManager<>(
                this,
                ExecutionCoordinator.simpleCoordinator(),
                senderFactory.get()
        );
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                manager.registerBrigadier();
                CloudBrigadierManager<Sender, ?> cbm = manager.brigadierManager();
                cbm.settings().set(BrigadierSetting.FORCE_EXECUTABLE, true);
            } catch (Throwable t) {
                LogUtil.error("Failed to register Brigadier native completions. Falling back to standard completions.", t);
            }
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
        return manager;
    }

    public BukkitSenderFactory getBukkitSenderFactory() {
        return LOADER.senderFactory.get();
    }
}
