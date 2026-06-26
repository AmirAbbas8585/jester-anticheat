package ac.jester.anticheat.command;

import ac.jester.anticheat.command.commands.*;
import ac.jester.anticheat.command.commands.SkyCPS;
import ac.jester.anticheat.command.commands.SkyClearViolations;
import ac.jester.anticheat.command.commands.SkyFreeze;
import ac.jester.anticheat.command.commands.SkyKnockback;
import ac.jester.anticheat.command.commands.SkyRotate;
import ac.jester.anticheat.command.commands.SkySetback;
import ac.jester.anticheat.command.commands.SkyStats;
import ac.jester.anticheat.command.commands.SkyTP;
import ac.jester.anticheat.command.handler.SkyCommandFailureHandler;
import ac.jester.anticheat.platform.api.command.CommandService;
import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.processors.requirements.RequirementApplicable;
import org.incendo.cloud.processors.requirements.RequirementApplicable.RequirementApplicableFactory;
import org.incendo.cloud.processors.requirements.RequirementPostprocessor;
import org.incendo.cloud.processors.requirements.Requirements;

import java.util.function.Function;
import java.util.function.Supplier;

public class CloudCommandService implements CommandService {

    public static final CloudKey<Requirements<Sender, SenderRequirement>> REQUIREMENT_KEY
            = CloudKey.of("requirements", new TypeToken<>() {});

    public static final RequirementApplicableFactory<Sender, SenderRequirement> REQUIREMENT_FACTORY
            = RequirementApplicable.factory(REQUIREMENT_KEY);

    private boolean commandsRegistered = false;

    private final Supplier<CommandManager<Sender>> commandManagerSupplier;
    private final CloudCommandAdapter commandAdapter;

    public CloudCommandService(Supplier<CommandManager<Sender>> commandManagerSupplier, CloudCommandAdapter commandAdapter) {
        this.commandManagerSupplier = commandManagerSupplier;
        this.commandAdapter = commandAdapter;
    }

    public void registerCommands() {
        if (commandsRegistered) return;
        CommandManager<Sender> commandManager = commandManagerSupplier.get();
        new SkyPerf().register(commandManager, commandAdapter);
        new SkyDebug().register(commandManager, commandAdapter);
        new SkyAlerts().register(commandManager, commandAdapter);
        new SkyProfile().register(commandManager, commandAdapter);
        new SkySendAlert().register(commandManager, commandAdapter);
        new SkyHelp().register(commandManager, commandAdapter);
        new SkyHistory().register(commandManager, commandAdapter);
        new SkyReload().register(commandManager, commandAdapter);
        new SkySpectate().register(commandManager, commandAdapter);
        new SkyStopSpectating().register(commandManager, commandAdapter);
        new SkyLog().register(commandManager, commandAdapter);
        new SkyVerbose().register(commandManager, commandAdapter);
        new SkyVersion().register(commandManager, commandAdapter);
        new SkyDump().register(commandManager, commandAdapter);
        new SkyBrands().register(commandManager, commandAdapter);
        new SkyList().register(commandManager, commandAdapter);
        new SkyFreeze().register(commandManager, commandAdapter);
        new SkyKnockback().register(commandManager, commandAdapter);
        new SkyRotate().register(commandManager, commandAdapter);
        new SkyCPS().register(commandManager, commandAdapter);
        new SkyClearViolations().register(commandManager, commandAdapter);
        new SkyTP().register(commandManager, commandAdapter);
        new SkyStats().register(commandManager, commandAdapter);
        new SkySetback().register(commandManager, commandAdapter);
        new SkyViolations().register(commandManager, commandAdapter);
        new SkyInfo().register(commandManager, commandAdapter);
        new SkyCheck().register(commandManager, commandAdapter);
        new SkyLogs().register(commandManager, commandAdapter);

        final RequirementPostprocessor<Sender, SenderRequirement>
                senderRequirementPostprocessor = RequirementPostprocessor.of(
                REQUIREMENT_KEY,
                new SkyCommandFailureHandler()
        );
        commandManager.registerCommandPostProcessor(senderRequirementPostprocessor);

        // Pretty, prefixed messages for every command error instead of cloud's
        // raw defaults. %prefix% is resolved by MessageUtil.miniMessage.
        registerExceptionHandler(commandManager, InvalidSyntaxException.class, e ->
                MessageUtil.miniMessage("%prefix% <red>Wrong usage. Try</red> <gray>/" + e.correctSyntax() + "</gray>"));
        registerExceptionHandler(commandManager, NoPermissionException.class, e ->
                MessageUtil.miniMessage("%prefix% <red>You don't have permission to use this command.</red>"));
        registerExceptionHandler(commandManager, InvalidCommandSenderException.class, e ->
                MessageUtil.miniMessage("%prefix% <red>This command can't be used from here.</red>"));
        registerExceptionHandler(commandManager, NoSuchCommandException.class, e ->
                MessageUtil.miniMessage("%prefix% <red>Unknown command. Try</red> <gray>/skyac help</gray>"));
        registerExceptionHandler(commandManager, ArgumentParseException.class, e -> {
            String reason = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : "could not parse argument";
            return MessageUtil.miniMessage("%prefix% <red>Invalid input:</red> <gray>"
                    + MiniMessage.miniMessage().escapeTags(reason) + "</gray>");
        });
        commandsRegistered = true;
    }

    protected <E extends Exception> void registerExceptionHandler(CommandManager<Sender> commandManager, Class<E> ex, Function<E, ComponentLike> toComponent) {
        commandManager.exceptionController().registerHandler(ex,
                (c) -> c.context().sender().sendMessage(toComponent.apply(c.exception()).asComponent().colorIfAbsent(NamedTextColor.RED))
        );
    }
}
