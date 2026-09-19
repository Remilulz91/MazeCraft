package fr.mazecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /maze administrative + utility commands:
 *   /maze version   - print mod version + build type
 *   /maze reload    - OP, reload config from disk
 *   /maze debug ... - DEBUG build only
 */
public class MazeCommand {

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(CommandManager.literal("maze")
                .then(CommandManager.literal("version")
                        .executes(MazeCommand::onVersion))
                .then(CommandManager.literal("reload")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(MazeCommand::onReload))
                .then(DebugCommand.build())
        );
    }

    private static int onVersion(CommandContext<ServerCommandSource> ctx) {
        String buildType = MazeCraft.isDebugBuild() ? "DEBUG" : "PUBLIC";
        ctx.getSource().sendFeedback(
                () -> Text.literal("MazeCraft v" + MazeCraft.getVersion() + " ").formatted(Formatting.GOLD)
                        .append(Text.literal("(" + buildType + " build)").formatted(Formatting.GRAY)),
                false
        );
        return 1;
    }

    private static int onReload(CommandContext<ServerCommandSource> ctx) {
        MazeCraftConfig.load();
        ctx.getSource().sendFeedback(
                () -> Text.translatable("mazecraft.command.reloaded").formatted(Formatting.GREEN),
                true
        );
        return 1;
    }
}
