package fr.mazecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.progression.MazeProgress;
import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * /maze administrative + utility commands:
 *   /maze version   - print mod version + build type
 *   /maze progress  - show your own maze progression (16 styles x 3 steps)
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
                .then(CommandManager.literal("progress")
                        .executes(MazeCommand::onProgress))
                .then(CommandManager.literal("reload")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(MazeCommand::onReload))
                .then(DebugCommand.build())
        );
    }

    /**
     * Shows the player their own progression: one line per style, with the step they cleared
     * last and the one they owe next. Styles of other dimensions are listed too, greyed out.
     */
    private static int onProgress(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.translatable("mazecraft.command.players_only"));
            return 0;
        }
        int done = MazeProgress.completedSteps(player);
        ctx.getSource().sendFeedback(() -> Text.translatable("mazecraft.progress.header",
                done, MazeProgress.TOTAL_STEPS).formatted(Formatting.GOLD), false);

        List<MazeStyle> here = MazeProgress.stylesOf(player.getWorld());
        for (MazeStyle style : MazeStyle.values()) {
            MutableText steps = Text.empty();
            for (MazeSize step : MazeSize.STEPS) {
                boolean cleared = MazeProgress.hasCleared(player, style, step);
                boolean next = !cleared && MazeProgress.canEnter(player, style, step);
                steps.append(Text.translatable("mazecraft.size." + step.id())
                        .formatted(cleared ? Formatting.GREEN : next ? Formatting.YELLOW : Formatting.DARK_GRAY));
                if (step != MazeSize.LARGE) steps.append(Text.literal(" > ").formatted(Formatting.DARK_GRAY));
            }
            Formatting nameColor = here.contains(style) ? Formatting.WHITE : Formatting.GRAY;
            ctx.getSource().sendFeedback(() -> Text.translatable("mazecraft.progress.line",
                    Text.translatable("mazecraft.style." + style.id()).formatted(nameColor), steps), false);
        }
        return 1;
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
