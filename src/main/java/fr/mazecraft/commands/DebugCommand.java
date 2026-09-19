package fr.mazecraft.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStyle;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;

/**
 * /maze debug subcommands. Only available in the DEBUG build (OP level 2).
 *
 *   /maze debug info
 *   /maze debug place <size> [style]   - build a maze centered on the player (not protected:
 *                                        it is not a real structure)
 *   /maze debug where                  - info about the natural maze you are standing in
 *   /maze debug unlock                 - mark that maze as conquered (protections off)
 *   /maze debug relock                 - mark it as not conquered again (gates are not rebuilt)
 *   /maze debug levers                 - position of every lever + state of every gate
 */
public class DebugCommand {

    private static final SuggestionProvider<ServerCommandSource> SIZE_SUGGESTIONS = (ctx, builder) -> {
        for (MazeSize s : MazeSize.values()) builder.suggest(s.id());
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> STYLE_SUGGESTIONS = (ctx, builder) -> {
        for (MazeStyle s : MazeStyle.values()) builder.suggest(s.id());
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("debug")
                .requires(src -> MazeCraft.isDebugBuild() && src.hasPermissionLevel(2))
                .then(CommandManager.literal("info")
                        .executes(DebugCommand::onInfo))
                .then(CommandManager.literal("where")
                        .executes(ctx -> onMazeState(ctx, null)))
                .then(CommandManager.literal("unlock")
                        .executes(ctx -> onMazeState(ctx, true)))
                .then(CommandManager.literal("relock")
                        .executes(ctx -> onMazeState(ctx, false)))
                .then(CommandManager.literal("levers")
                        .executes(DebugCommand::onLevers))
                .then(CommandManager.literal("place")
                        .then(CommandManager.argument("size", StringArgumentType.word())
                                .suggests(SIZE_SUGGESTIONS)
                                .executes(ctx -> onPlace(ctx, MazeStyle.HEDGE.id()))
                                .then(CommandManager.argument("style", StringArgumentType.word())
                                        .suggests(STYLE_SUGGESTIONS)
                                        .executes(ctx -> onPlace(ctx, StringArgumentType.getString(ctx, "style"))))));
    }

    private static int onInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        MazeCraftConfig cfg = MazeCraftConfig.get();
        src.sendFeedback(() -> Text.literal("═══ MazeCraft DEBUG INFO ═══").formatted(Formatting.LIGHT_PURPLE), false);
        src.sendFeedback(() -> Text.literal("Version: " + MazeCraft.getVersion()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Build type: DEBUG").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("protectUntilSolved: " + cfg.protectUntilSolved
                + ", preventWallWalking: " + cfg.preventWallWalking).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Size weights (S/M/L/C): " + cfg.weightSmall + "/" + cfg.weightMedium
                + "/" + cfg.weightLarge + "/" + cfg.weightColossal).formatted(Formatting.GRAY), false);
        return 1;
    }

    /**
     * Builds a maze centered on the command source, floor one block under its feet.
     * Uses exactly the same piece as world generation, applied to the whole box at once.
     */
    private static int onPlace(CommandContext<ServerCommandSource> ctx, String styleId) {
        ServerCommandSource src = ctx.getSource();
        String sizeId = StringArgumentType.getString(ctx, "size");
        MazeSize size = MazeSize.fromId(sizeId);
        if (size == null) {
            src.sendError(Text.literal("[DEBUG] Unknown size: " + sizeId));
            return 0;
        }
        MazeStyle style = MazeStyle.fromId(styleId);
        if (style == null) {
            src.sendError(Text.literal("[DEBUG] Unknown style: " + styleId));
            return 0;
        }

        ServerWorld world = src.getWorld();
        BlockPos origin = BlockPos.ofFloored(src.getPosition());
        long seed = world.getRandom().nextLong();
        int floorY = origin.getY() - 1;

        MazePiece piece = new MazePiece(style, size, seed, origin.getX(), floorY, origin.getZ(), -1);
        BlockBox box = piece.getBoundingBox();
        piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(),
                Random.create(seed), box, new ChunkPos(origin), origin);

        src.sendFeedback(() -> Text.literal("[DEBUG] Placed " + size.id() + " " + style.id() + " maze ("
                + size.span() + "×" + size.span() + ") centered on " + origin.getX() + " " + floorY + " " + origin.getZ()
                + " — seed " + seed).formatted(Formatting.LIGHT_PURPLE), true);
        return 1;
    }

    /**
     * where (solve == null), unlock (true) or relock (false) the natural maze at the source position.
     */
    private static int onMazeState(CommandContext<ServerCommandSource> ctx, Boolean solve) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        BlockPos pos = BlockPos.ofFloored(src.getPosition());
        MazeFinder.MazeHit hit = MazeFinder.find(world, pos, 64);
        if (hit == null) {
            src.sendError(Text.literal("[DEBUG] No natural maze here (mazes from /maze debug place are not tracked)."));
            return 0;
        }
        MazeState state = MazeState.get(world);
        if (solve != null) {
            if (solve) {
                state.markSolved(hit.key());
            } else {
                state.reset(hit.key());
                state.resetGates(hit.key());
            }
        }
        MazePiece maze = hit.piece();
        BlockPos chest = maze.chestPos();
        BlockPos entrance = maze.entranceOutsidePos();
        boolean solved = state.isSolved(hit.key());
        src.sendFeedback(() -> Text.literal("[DEBUG] " + maze.getSize().id() + " " + maze.getStyle().id()
                + " maze — " + (solved ? "CONQUERED" : "locked")
                + " — chest " + chest.toShortString() + " — entrance " + entrance.toShortString())
                .formatted(Formatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int onLevers(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        MazeFinder.MazeHit hit = MazeFinder.find(world, BlockPos.ofFloored(src.getPosition()), 64);
        if (hit == null) {
            src.sendError(Text.literal("[DEBUG] No natural maze here."));
            return 0;
        }
        MazeState state = MazeState.get(world);
        MazePiece maze = hit.piece();
        for (int i = 0; i < maze.gateCount(); i++) {
            final int k = i;
            boolean open = state.isGateOpen(hit.key(), k);
            BlockPos gate = maze.gateBlocks(k).get(0);
            src.sendFeedback(() -> Text.literal("[DEBUG] Gate " + k + (k == maze.gateCount() - 1 ? " (plaza)" : "")
                    + ": " + (open ? "OPEN" : "closed") + " at " + gate.toShortString()
                    + " — lever at " + maze.leverPos(k).toShortString())
                    .formatted(open ? Formatting.GREEN : Formatting.LIGHT_PURPLE), false);
        }
        return 1;
    }
}
