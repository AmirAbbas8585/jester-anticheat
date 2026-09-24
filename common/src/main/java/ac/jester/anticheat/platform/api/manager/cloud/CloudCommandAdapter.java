package ac.jester.anticheat.platform.api.manager.cloud;

import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.manager.CommandAdapter;
import ac.jester.anticheat.platform.api.sender.Sender;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.SuggestionProvider;

public interface CloudCommandAdapter extends CommandAdapter {
    ParserDescriptor<Sender, PlayerSelector> singlePlayerSelectorParser();

    SuggestionProvider<Sender> onlinePlayerSuggestions();
}
