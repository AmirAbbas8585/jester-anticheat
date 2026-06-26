package ac.jester.anticheat.command;

import ac.jester.anticheat.command.commands.*;
import ac.jester.anticheat.command.commands.JesterCPS;
import ac.jester.anticheat.command.commands.JesterClearViolations;
import ac.jester.anticheat.command.commands.JesterFreeze;
import ac.jester.anticheat.command.commands.JesterKnockback;
import ac.jester.anticheat.command.commands.JesterRotate;
import ac.jester.anticheat.command.commands.JesterSetback;
import ac.jester.anticheat.command.commands.JesterStats;
import ac.jester.anticheat.command.commands.JesterTP;
import ac.jester.anticheat.command.handler.JesterCommandFailureHandler;
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
        new JesterPerf().register(commandManager, commandAdapter);
        new JesterDebug().register(commandManager, commandAdapter);
        new JesterAlerts().register(commandManager, commandAdapter);
        new JesterProfile().register(commandManager, commandAdapter);
        new JesterHelp().register(commandManager, commandAdapter);
        new JesterHistory().register(commandManager, commandAdapter);
        new JesterReload().register(commandManager, commandAdapter);
        new JesterSpectate().register(commandManager, commandAdapter);
        new JesterStopSpectating().register(commandManager, commandAdapter);
        new JesterLog().register(commandManager, commandAdapter);
        new JesterVerbose().register(commandManager, commandAdapter);
        new JesterVersion().register(commandManager, commandAdapter);
        new JesterDump().register(commandManager, commandAdapter);
        new JesterBrands().register(commandManager, commandAdapter);
        new JesterList().register(commandManager, commandAdapter);
        new JesterFreeze().register(commandManager, commandAdapter);
        new JesterKnockback().register(commandManager, commandAdapter);
        new JesterRotate().register(commandManager, commandAdapter);
        new JesterCPS().register(commandManager, commandAdapter);
        new JesterClearViolations().register(commandManager, commandAdapter);
        new JesterTP().register(commandManager, commandAdapter);
        new JesterStats().register(commandManager, commandAdapter);
        new JesterSetback().register(commandManager, commandAdapter);
        new JesterViolations().register(commandManager, commandAdapter);
        new JesterInfo().register(commandManager, commandAdapter);
        new JesterCheck().register(commandManager, commandAdapter);
        new JesterLogs().register(commandManager, commandAdapter);

        final RequirementPostprocessor<Sender, SenderRequirement>
                senderRequirementPostprocessor = RequirementPostprocessor.of(
                REQUIREMENT_KEY,
                new JesterCommandFailureHandler()
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
                MessageUtil.miniMessage("%prefix% <red>Unknown command. Try</red> <gray>/jester help</gray>"));
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
