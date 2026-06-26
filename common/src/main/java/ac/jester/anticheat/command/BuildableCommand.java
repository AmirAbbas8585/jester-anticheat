package ac.jester.anticheat.command;

import ac.jester.anticheat.platform.api.manager.cloud.CloudCommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;

public interface BuildableCommand {
    void register(CommandManager<Sender> manager, CloudCommandAdapter adapter);
}
