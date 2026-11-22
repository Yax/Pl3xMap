package net.pl3x.map.fabric.server.command;

import java.util.stream.Collectors;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.command.Sender;
import net.pl3x.map.core.command.exception.PlayerParseException;
import net.pl3x.map.core.player.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Parses the name of an online player.
 */
@NullMarked
public class PlayerParser implements ArgumentParser<Sender, Player>, BlockingSuggestionProvider.Strings<Sender> {
    public static ParserDescriptor<Sender, Player> playerParser() {
        return ParserDescriptor.of(new PlayerParser(), Player.class);
    }

    @Override
    public ArgumentParseResult<Player> parse(CommandContext<Sender> context, CommandInput input) {
        if (!input.hasRemainingInput(true)) {
            return ArgumentParseResult.failure(new PlayerParseException(null, PlayerParseException.MUST_SPECIFY_PLAYER));
        }
        String name = input.peekString();
        Player player = Pl3xMap.api().getPlayerRegistry().get(name);
        if (player == null) {
            return ArgumentParseResult.failure(new PlayerParseException(name, PlayerParseException.NO_SUCH_PLAYER));
        }
        input.readString();
        return ArgumentParseResult.success(player);
    }

    @Override
    public Iterable<String> stringSuggestions(CommandContext<Sender> context, CommandInput input) {
        return Pl3xMap.api().getPlayerRegistry().values().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }
}
