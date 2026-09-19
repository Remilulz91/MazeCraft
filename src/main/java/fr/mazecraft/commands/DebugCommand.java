package fr.mazecraft.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /maze debug subcommands. Only available when the build is DEBUG
 * AND the config flag {@code enableDebugCommands} is true.
 *
 * Available in DEBUG build only:
 *   /maze debug info
 *   (v0.1) /maze debug place <style> <size>
 */
public class DebugCommand {

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("debug")
                .requires(src -> MazeCraft.isDebugBuild()
                        && MazeCraftConfig.get().enableDebugCommands
                        && src.hasPermissionLevel(2))
                .then(CommandManager.literal("info")
                        .executes(DebugCommand::onInfo));
    }

    private static int onInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("═══ MazeCraft DEBUG INFO ═══").formatted(Formatting.LIGHT_PURPLE), false);
        src.sendFeedback(() -> Text.literal("Version: " + MazeCraft.getVersion()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Build type: DEBUG").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("enableDebugCommands: " + MazeCraftConfig.get().enableDebugCommands).formatted(Formatting.GRAY), false);
        return 1;
    }
}
