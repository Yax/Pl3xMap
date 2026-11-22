package net.pl3x.map.fabric.server.command;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.pl3x.map.core.command.Sender;
import net.pl3x.map.core.command.exception.PointParseException;
import net.pl3x.map.core.markers.Point;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Parses a block column as {@code <x> <z>}. Each value can be absolute or relative to the sender ({@code ~}, {@code ~5}).
 */
@NullMarked
public class ColumnPosParser implements ArgumentParser<Sender, Point>, BlockingSuggestionProvider.Strings<Sender> {
    public static ParserDescriptor<Sender, Point> columnPosParser() {
        return ParserDescriptor.of(new ColumnPosParser(), Point.class);
    }

    @Override
    public ArgumentParseResult<Point> parse(CommandContext<Sender> context, CommandInput input) {
        if (input.remainingTokens() < 2) {
            return ArgumentParseResult.failure(new PointParseException(input.remainingInput(), PointParseException.INVALID_FORMAT));
        }
        Vec3 origin = context.sender().<CommandSourceStack>getSender().getPosition();
        String rawX = input.readString();
        String rawZ = input.readString();
        try {
            return ArgumentParseResult.success(Point.of(coordinate(rawX, origin.x), coordinate(rawZ, origin.z)));
        } catch (NumberFormatException e) {
            return ArgumentParseResult.failure(new PointParseException(rawX + " " + rawZ, PointParseException.INVALID_FORMAT));
        }
    }

    private static int coordinate(String raw, double origin) {
        if (raw.startsWith("~")) {
            String offset = raw.substring(1);
            return Mth.floor(origin + (offset.isEmpty() ? 0 : Double.parseDouble(offset)));
        }
        return Mth.floor(Double.parseDouble(raw));
    }

    @Override
    public Iterable<String> stringSuggestions(CommandContext<Sender> context, CommandInput input) {
        return List.of("~");
    }
}
