package net.pl3x.map.fabric.server;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import org.jspecify.annotations.NullMarked;

/**
 * Sends Adventure components to command sources and players.
 * <p>
 * Pl3xMap only sends chat messages, so this converts each component through JSON
 * to a vanilla component instead of depending on adventure-platform-fabric.
 */
@NullMarked
public class FabricAudiences implements AudienceProvider {
    private final MinecraftServer server;

    public FabricAudiences(MinecraftServer server) {
        this.server = server;
    }

    public net.minecraft.network.chat.Component toNative(Component component) {
        JsonElement json = GsonComponentSerializer.gson().serializeToTree(component);
        return ComponentSerialization.CODEC
                .parse(this.server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow();
    }

    public Audience audience(CommandSourceStack source) {
        return new Audience() {
            @Override
            public void sendMessage(Component message) {
                source.sendSystemMessage(toNative(message));
            }
        };
    }

    public Audience audience(ServerPlayer player) {
        return new Audience() {
            @Override
            public void sendMessage(Component message) {
                player.sendSystemMessage(toNative(message));
            }
        };
    }

    private Audience players(Predicate<ServerPlayer> filter) {
        return Audience.audience(() -> {
            List<ServerPlayer> players = this.server.getPlayerList().getPlayers();
            return players.stream().filter(filter).map(this::audience).toList().iterator();
        });
    }

    @Override
    public Audience all() {
        return Audience.audience(console(), players());
    }

    @Override
    public Audience console() {
        return audience(this.server.createCommandSourceStack());
    }

    @Override
    public Audience players() {
        return players(player -> true);
    }

    @Override
    public Audience player(UUID playerId) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
        return player == null ? Audience.empty() : audience(player);
    }

    @Override
    public Audience permission(String permission) {
        // no permissions mod support; same fallback as the commands: the server's op level
        Permission opLevel = new Permission.HasCommandLevel(this.server.operatorUserPermissions().level());
        return players(player -> player.createCommandSourceStack().permissions().hasPermission(opLevel));
    }

    @Override
    public Audience world(Key world) {
        String name = world.asString();
        return players(player -> player.level().dimension().identifier().toString().equals(name));
    }

    @Override
    public Audience server(String serverName) {
        return all();
    }

    @Override
    public ComponentFlattener flattener() {
        return ComponentFlattener.basic();
    }

    @Override
    public void close() {
    }
}
