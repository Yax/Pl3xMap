package net.pl3x.map.fabric.server.command;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.command.Sender;
import net.pl3x.map.core.command.parser.PlatformParsers;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.player.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class FabricParsers implements PlatformParsers {
    @Override
    public ParserDescriptor<Sender, ?> columnPosParser() {
        return ColumnPosParser.columnPosParser();
    }

    @Override
    public Point resolvePointFromColumnPos(String name, CommandContext<Sender> context) {
        return context.getOrDefault(name, Point.ZERO);
    }

    @Override
    public ParserDescriptor<Sender, ?> playerSelectorParser() {
        return PlayerParser.playerParser();
    }

    @Override
    public @Nullable Player resolvePlayerFromPlayerSelector(String name, CommandContext<Sender> context) {
        Player player = context.<Player>getOrDefault(name, null);
        if (player != null) {
            return player;
        }
        if (context.sender() instanceof Sender.Player<?> senderPlayer) {
            return Pl3xMap.api().getPlayerRegistry().get(senderPlayer.getUUID());
        }
        return null;
    }
}
