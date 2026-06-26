package ac.jester.anticheat.platform.bukkit.command;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.platform.api.command.AbstractPlayerSelectorParser;
import ac.jester.anticheat.platform.api.command.PlayerSelector;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.platform.bukkit.sender.BukkitSenderFactory;
import org.bukkit.entity.Player;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Player argument parser for staff commands.
 *
 * Uses cloud's plain PlayerParser (online player names only) instead of the
 * vanilla SinglePlayerSelectorParser — staff commands should suggest player
 * names, not Minecraft entity selectors like @a / @e / @e[advancements=...].
 */
public class BukkitPlayerSelectorParser<C> extends AbstractPlayerSelectorParser<C> {

    @Override
    public ParserDescriptor<C, PlayerSelector> descriptor() {
        return super.createDescriptor();
    }

    @Override
    protected ParserDescriptor<C, ?> getPlatformSpecificDescriptor() {
        return PlayerParser.playerParser();
    }

    @Override
    protected CompletableFuture<PlayerSelector> adaptToCommonSelector(CommandContext<C> context, Object platformSpecificSelector) {
        return CompletableFuture.completedFuture(new DirectPlayerAdapter((Player) platformSpecificSelector));
    }

    /** PlayerSelector backed by a directly-parsed online player. */
    private record DirectPlayerAdapter(Player player) implements PlayerSelector {

        @Override
        public boolean isSingle() {
            return true;
        }

        @Override
        public Sender getSinglePlayer() {
            return ((BukkitSenderFactory) GrimAPI.INSTANCE.getSenderFactory()).map(player);
        }

        @Override
        public Collection<Sender> getPlayers() {
            return Collections.singletonList(getSinglePlayer());
        }

        @Override
        public String inputString() {
            return player.getName();
        }
    }
}
