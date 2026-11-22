package net.pl3x.map.fabric.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.StringArgument;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.commands.suggestions.SuggestionProviders;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.pl3x.map.core.command.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.suggestion.Suggestion;
import org.jspecify.annotations.NullMarked;

/**
 * A small cloud command manager for the Fabric server.
 * <p>
 * Each cloud root command becomes one Brigadier literal with one greedy string argument.
 * Cloud parses, checks permissions, executes and suggests the rest. The tree only uses
 * vanilla argument and suggestion types, so it does not depend on how a Minecraft version
 * serializes or checks command nodes.
 */
@NullMarked
public class FabricServerCommandManager extends CommandManager<Sender> {
    private static final String ARGUMENTS = "args";

    private final Set<String> rootLiterals;

    public FabricServerCommandManager() {
        this(ConcurrentHashMap.newKeySet());
    }

    private FabricServerCommandManager(Set<String> rootLiterals) {
        super(ExecutionCoordinator.simpleCoordinator(), command -> {
            CommandComponent<Sender> root = command.rootComponent();
            rootLiterals.add(root.name());
            rootLiterals.addAll(root.alternativeAliases());
            return true;
        });
        this.rootLiterals = rootLiterals;
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> registerRoots(dispatcher));
    }

    @Override
    public boolean hasPermission(Sender sender, String permission) {
        if (permission.isEmpty()) {
            return true;
        }
        CommandSourceStack source = sender.getSender();
        MinecraftServer server = source.getServer();
        // same default as cloud-fabric without a permissions mod: the server's op level
        return source.permissions().hasPermission(new Permission.HasCommandLevel(server.operatorUserPermissions().level()));
    }

    private void registerRoots(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String literal : this.rootLiterals) {
            dispatcher.register(Commands.literal(literal)
                    .executes(this::execute)
                    .then(Commands.argument(ARGUMENTS, StringArgument.greedyString())
                            .suggests(SuggestionProviders.serverOnly(this::suggest))
                            .executes(this::execute)));
        }
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        String input = context.getInput().substring(rootStart(context.getLastChild()));
        commandExecutor().executeCommand(FabricSender.create(context.getSource()), input);
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggest(MinecraftServer server, CommandContext<? extends SuggestionContext> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource().entity() instanceof ServerPlayer player
                ? player.createCommandSourceStack()
                : server.createCommandSourceStack();
        String input = builder.getInput().substring(rootStart(context.getLastChild()));
        return suggestionFactory().suggest(FabricSender.create(source), input).thenApply(result -> {
            int trimmed = builder.getInput().length() - result.commandInput().length();
            SuggestionsBuilder offset = builder.createOffset(trimmed + result.commandInput().cursor());
            for (Suggestion suggestion : result.list()) {
                offset.suggest(suggestion.suggestion());
            }
            return offset.build();
        });
    }

    private static int rootStart(CommandContext<?> context) {
        List<? extends ParsedCommandNode<?>> nodes = context.getNodes();
        return nodes.isEmpty() ? 0 : nodes.getFirst().getRange().getStart();
    }
}
