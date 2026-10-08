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
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
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
 *   /maze debug colossal               - hunt down a colossal maze and go to its entrance
 *   /maze debug vault [open]           - the vault of this maze: go to it, optionally unseal it
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
                .then(CommandManager.literal("kronos")
                        .executes(DebugCommand::onKronos))
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
                .then(CommandManager.literal("colossal")
                        .executes(DebugCommand::onColossal))
                .then(CommandManager.literal("vault")
                        .executes(ctx -> onVault(ctx, false))
                        .then(CommandManager.literal("open")
                                .executes(ctx -> onVault(ctx, true))))
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
        return 1;
    }

    /**
     * DEBUG: finds the Labyrinth of Kronos and teleports to the foot of its shaft.
     * Without this the whole vault is untestable until all 48 mazes have been cleared.
     */
    private static int onKronos(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.translatable("mazecraft.command.players_only"));
            return 0;
        }
        ServerWorld world = src.getWorld();
        TagKey<Structure> tag = TagKey.of(RegistryKeys.STRUCTURE, MazeCraft.id("kronos"));
        BlockPos found = world.locateStructure(tag, player.getBlockPos(), 200, false);
        if (found == null) {
            src.sendFeedback(() -> Text.translatable("mazecraft.command.kronos_not_found")
                    .formatted(Formatting.RED), false);
            return 0;
        }
        int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, found.getX(), found.getZ());
        player.teleport(world, found.getX() + 0.5, surface + 1, found.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        src.sendFeedback(() -> Text.translatable("mazecraft.command.kronos_found",
                found.getX(), surface, found.getZ()).formatted(Formatting.LIGHT_PURPLE), false);
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

        lastPlaced = piece;
        src.sendFeedback(() -> Text.literal("[DEBUG] Placed " + size.id() + " " + style.id() + " maze ("
                + size.span() + "×" + size.span() + ") centered on " + origin.getX() + " " + floorY + " " + origin.getZ()
                + " — seed " + seed).formatted(Formatting.LIGHT_PURPLE), true);
        return 1;
    }

    /**
     * The last maze built by {@code /maze debug place}.
     *
     * <p>A placed maze is not a structure: nothing in the world knows it is there, so
     * {@link MazeFinder} cannot find it and none of the commands that start from "the maze you
     * are standing in" work on one. Keeping the piece means {@code /maze debug vault} does —
     * which is the whole point of being able to place a colossal maze on demand.</p>
     */
    private static MazePiece lastPlaced;

    /** The last maze from {@code /maze debug place}, so the keypad works on one too. */
    public static MazePiece lastPlaced() {
        return lastPlaced;
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

    /**
     * DEBUG: the vault of the colossal maze you are standing in — where it is, and, with
     * {@code open}, its door taken down.
     *
     * <p>Without this the room is only reachable by walking the deepest dead end of a 221-block
     * maze, and the treasure chamber behind the door is not reachable at all until the code
     * exists. Both have to be looked at before either is finished.</p>
     */
    private static int onVault(CommandContext<ServerCommandSource> ctx, boolean open) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        MazeFinder.MazeHit hit = MazeFinder.find(world, BlockPos.ofFloored(src.getPosition()), 64);
        // Fall back on the last maze from /maze debug place: it is not a structure, so the
        // lookup above can never find it, and placing a colossal maze on demand is how the
        // vault gets looked at without hunting one in the world first.
        MazePiece maze = hit != null ? hit.piece() : lastPlaced;
        if (maze == null) {
            src.sendError(Text.literal("[DEBUG] No maze here, and none placed this session. "
                    + "Try /maze debug colossal, or /maze debug place colossal <style>."));
            return 0;
        }
        if (!maze.hasVault() || maze.vaultEntrance() == null) {
            src.sendError(Text.literal("[DEBUG] This maze has no vault ("
                    + maze.getSize().id() + " " + maze.getStyle().id()
                    + ") — only colossal mazes have one, Kronos excepted."));
            return 0;
        }
        MazeState state = MazeState.get(world);
        BlockPos stair = maze.vaultEntrance();
        BlockPos floor = maze.vaultFloorPos();
        String door = hit == null ? "placed maze, not tracked"
                : (state.isVaultOpen(hit.key()) ? "OPEN" : "sealed");
        src.sendFeedback(() -> Text.literal("[DEBUG] Vault stair at " + stair.toShortString()
                + ", floor at " + floor.toShortString()
                + ", chest at " + maze.vaultChestPos().toShortString()
                + " — door " + door).formatted(Formatting.LIGHT_PURPLE), false);

        StringBuilder code = new StringBuilder();
        for (int digit : maze.vaultCode()) code.append(digit);
        src.sendFeedback(() -> Text.literal("[DEBUG] Code " + code).formatted(Formatting.GOLD), false);
        for (int slot = 0; slot < fr.mazecraft.block.CodeKeyBlock.SLOTS; slot++) {
            BlockPos plaque = maze.codePlaquePos(slot);
            final int n = slot + 1;
            src.sendFeedback(() -> Text.literal("[DEBUG]   digit " + n + " on a plaque at "
                    + (plaque == null ? "nowhere — this maze lost one" : plaque.toShortString()))
                    .formatted(plaque == null ? Formatting.RED : Formatting.GRAY), false);
        }

        if (open) {
            if (hit != null) state.openVault(hit.key());
            maze.openVault(world);
            src.sendFeedback(() -> Text.literal("[DEBUG] Door opened.").formatted(Formatting.GREEN), false);
        }

        ServerPlayerEntity player = src.getPlayer();
        if (player != null) {
            player.teleport(world, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5,
                    player.getYaw(), player.getPitch());
            player.fallDistance = 0;
        }
        return 1;
    }

    /**
     * How long the search is allowed to run before it gives up, whatever it has found.
     *
     * <p>Measured the hard way: at 32 probes of radius 11 this command ran for <b>half an hour
     * to an hour</b>. The reason is intrinsic and worth writing down. A large maze has a
     * candidate chunk in every 40-chunk cell of its grid, 16 styles over, so the candidates are
     * dense — but almost every one is then thrown out for sloping ground, water, the wrong biome
     * or another structure too close, and each of those rejections costs a full terrain
     * evaluation. Working back from that hour: about 7 ms per candidate, and roughly 7 500
     * candidates evaluated for every maze that actually exists. Twenty-five mazes examined is
     * therefore half an hour of server time, and no amount of tuning changes that — it is what
     * the generator costs.</p>
     *
     * <p>So the search is capped instead of made clever. It does what it can in twenty seconds
     * and says plainly that it ran out of time. For iterating on the vault itself, {@code /maze
     * debug place colossal <style>} is instant and builds the identical piece.</p>
     */
    private static final long COLOSSAL_BUDGET_NANOS = 20L * 1_000_000_000L;

    /** Upper bound on probes; the time budget is what normally stops the search. */
    private static final int COLOSSAL_PROBES = 24;

    /**
     * Search radius around each probe, <b>in grid cells, not chunks</b>.
     *
     * <p>This is the trap in {@code locateStructure}: its radius is multiplied by the structure
     * set's spacing, which is 40 chunks for a large maze. The first version of this command
     * passed 44 meaning "44 chunks" and was really asking for 1760 chunks — 28 000 blocks — so
     * every probe searched almost the same enormous area, every probe found something, and
     * forty-eight of them came back with the same three mazes.</p>
     *
     * <p>Three cells is 120 chunks, about 1 900 blocks. Deliberately small: a probe that finds
     * nothing has to sweep its whole square, and the square is what costs the time — 7 × 7 cells
     * across 16 styles is under 800 terrain evaluations, a few seconds at worst, so one bad
     * probe can no longer eat the whole budget on its own. A probe that does find something
     * stops at the first ring that has one and costs almost nothing.</p>
     */
    private static final int COLOSSAL_RADIUS = 3;

    /**
     * How far apart the probes are spread, in blocks — twice the search radius, so the probes
     * tile the ground without overlapping and each one's nearest maze is its own rather than
     * its neighbour's.
     */
    private static final int COLOSSAL_STRIDE = 3800;

    /**
     * DEBUG: finds a colossal maze and teleports to its entrance.
     *
     * <p>There is no {@code /locate} for a colossal maze and there cannot be one: colossal is not
     * a structure of its own but a one-in-{@value MazeSize#COLOSSAL_CHANCE} upgrade rolled on a
     * large maze when the ground under it is flat enough. The only way to know is to look at the
     * mazes themselves, which is what this does — it walks a spiral of probes outward, locates
     * the nearest large maze to each, and reads the size the roll actually gave it.</p>
     */
    private static int onColossal(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        BlockPos from = BlockPos.ofFloored(src.getPosition());
        TagKey<Structure> large = TagKey.of(RegistryKeys.STRUCTURE, MazeCraft.id("size/large"));

        src.sendFeedback(() -> Text.literal("[DEBUG] Looking for a colossal maze — up to "
                + (COLOSSAL_BUDGET_NANOS / 1_000_000_000L) + " seconds.").formatted(Formatting.GRAY), false);

        long deadline = System.nanoTime() + COLOSSAL_BUDGET_NANOS;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int looked = 0, empty = 0, unreadable = 0, reach = 0;
        boolean ranOut = false;
        MazePiece nearest = null;
        for (int probe = 0; probe < COLOSSAL_PROBES; probe++) {
            if (System.nanoTime() > deadline) { ranOut = true; break; }
            BlockPos origin = spiralProbe(from, probe);
            reach = Math.max(reach, Math.max(Math.abs(origin.getX() - from.getX()),
                    Math.abs(origin.getZ() - from.getZ())) + COLOSSAL_RADIUS * 40 * 16);
            BlockPos found = world.locateStructure(large, origin, COLOSSAL_RADIUS, false);
            if (found == null) { empty++; continue; }
            if (!seen.add(BlockPos.asLong(found.getX(), 0, found.getZ()))) continue;
            looked++;
            // findInColumn, not find: what locateStructure hands back is the candidate chunk's
            // corner at y = 0, never the maze's own height, so a lookup that checks Y rejects
            // every surface maze there is.
            MazeFinder.MazeHit hit = MazeFinder.findInColumn(world, found);
            if (hit == null) { unreadable++; continue; }
            if (nearest == null) nearest = hit.piece();
            if (hit.piece().getSize() != MazeSize.COLOSSAL) continue;

            MazePiece maze = hit.piece();
            BlockPos door = maze.entranceOutsidePos();
            final int n = looked;
            src.sendFeedback(() -> Text.literal("[DEBUG] Colossal " + maze.getStyle().id()
                    + " maze at " + maze.centerPos().toShortString() + " — entrance "
                    + door.toShortString() + " (" + n + " large mazes looked at)")
                    .formatted(Formatting.LIGHT_PURPLE), false);
            ServerPlayerEntity player = src.getPlayer();
            if (player != null) {
                player.teleport(world, door.getX() + 0.5, door.getY(), door.getZ() + 0.5,
                        player.getYaw(), player.getPitch());
                player.fallDistance = 0;
            }
            return 1;
        }
        final String where = nearest == null ? "none readable"
                : "nearest large: " + nearest.getStyle().id() + " at "
                        + nearest.centerPos().toShortString();
        src.sendError(Text.literal("[DEBUG] No colossal maze found"
                + (ranOut ? " (out of time after " + (COLOSSAL_BUDGET_NANOS / 1_000_000_000L) + "s)"
                          : " within " + (reach / 1000) + "k blocks of you")
                + " — " + looked + " large mazes looked at, " + empty + " probes found nothing"
                + (unreadable > 0 ? ", " + unreadable + " unreadable" : "")
                + " (" + where + "). Hunting a natural colossal maze is slow by nature: the"
                + " generator throws out almost every candidate it tests, so examining a few"
                + " dozen mazes is minutes of work. For testing, /maze debug place colossal"
                + " <style> is instant and builds the identical maze."));
        return 0;
    }

    /** Probe {@code i} of a square spiral around {@code from}, {@link #COLOSSAL_STRIDE} apart. */
    private static BlockPos spiralProbe(BlockPos from, int i) {
        if (i == 0) return from;
        // Ring r holds 8r probes; walk round it rather than over the same ground twice.
        int r = 1;
        int index = i - 1;
        while (index >= 8 * r) { index -= 8 * r; r++; }
        int side = index / (2 * r), step = index % (2 * r) - r;
        int dx = switch (side) { case 0 -> step; case 1 -> r; case 2 -> -step; default -> -r; };
        int dz = switch (side) { case 0 -> -r; case 1 -> step; case 2 -> r; default -> -step; };
        return from.add(dx * COLOSSAL_STRIDE, 0, dz * COLOSSAL_STRIDE);
    }
}
