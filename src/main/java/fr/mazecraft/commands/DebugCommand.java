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
import fr.mazecraft.structure.MazeStructure;
import fr.mazecraft.structure.MazeStyle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
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
                .then(CommandManager.literal("placement")
                        .then(CommandManager.argument("style", StringArgumentType.word())
                                .suggests(STYLE_SUGGESTIONS)
                                .executes(ctx -> onPlacement(ctx, MazeSize.SMALL.id()))
                                .then(CommandManager.argument("size", StringArgumentType.word())
                                        .suggests(SIZE_SUGGESTIONS)
                                        .executes(ctx -> onPlacement(ctx,
                                                StringArgumentType.getString(ctx, "size")))
                                        .then(CommandManager.literal("scan")
                                                .executes(ctx -> onPlacementScan(ctx,
                                                        StringArgumentType.getString(ctx, "size"), false, -1, 0)))
                                        .then(CommandManager.literal("hunt")
                                                .executes(ctx -> onPlacementScan(ctx,
                                                        StringArgumentType.getString(ctx, "size"), true, -1, 0))
                                                .then(CommandManager.argument("relief",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> onPlacementScan(ctx,
                                                                StringArgumentType.getString(ctx, "size"), true,
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "relief"), 0))
                                                        .then(CommandManager.argument("mindrop",
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 64))
                                                                .executes(ctx -> onPlacementScan(ctx,
                                                                        StringArgumentType.getString(ctx, "size"), true,
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "relief"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "mindrop")))))))))
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

    /**
     * DEBUG: asks the placement rules, at the player's feet, whether a maze of this style and
     * size could stand here — and when the answer is no, which test refused and by how much.
     *
     * <p>Built because two styles out of sixteen turned out to be nearly unfindable on a
     * server, and my explanation for it was a correlation over eight compass searches. A
     * correlation is not a reason to change how every maze in the mod generates. This asks the
     * rule itself, through the same {@link MazeStructure#surveyFloor} that worldgen calls and
     * the same chunk generator the world was made with, so what it prints is what generation
     * decided, not a second implementation of it that agrees today and drifts tomorrow.</p>
     */
    private static int onPlacement(CommandContext<ServerCommandSource> ctx, String sizeId) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.translatable("mazecraft.command.players_only"));
            return 0;
        }
        MazeStyle style = MazeStyle.fromId(StringArgumentType.getString(ctx, "style"));
        MazeSize size = MazeSize.fromId(sizeId);
        if (style == null || size == null) {
            src.sendError(Text.literal("Unknown style or size."));
            return 0;
        }

        ServerWorld world = src.getWorld();
        BlockPos at = player.getBlockPos();
        String id = style.isKronos() ? "maze_kronos" : "maze_" + style.id() + "_" + size.step().id();
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE)
                .get(MazeCraft.id(id));
        if (structure == null) {
            src.sendError(Text.literal("No structure " + id + " in the registry."));
            return 0;
        }

        src.sendFeedback(() -> Text.literal("=== placement " + id + " @ "
                + at.getX() + ", " + at.getZ() + " ===").formatted(Formatting.LIGHT_PURPLE), false);

        // 1. Biome, read from the structure's own biome list rather than a copy of it here.
        boolean biomeOk = structure.getValidBiomes().contains(world.getBiome(at));
        src.sendFeedback(() -> Text.literal("biome   " + world.getBiome(at).getKey()
                        .map(k -> k.getValue().toString()).orElse("?")
                        + (biomeOk ? "  ACCEPTED" : "  REFUSED (not in this style's list)"))
                .formatted(biomeOk ? Formatting.GREEN : Formatting.RED), false);

        // 2. Terrain. Twice: once with the real thresholds, to report the verdict, and once
        //    with none, to report what the ground actually is. The verdict alone misleads —
        //    it stops at the first test that fails, so "relief 13" from a judging run is a
        //    lower bound, not the site's relief.
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        NoiseConfig noise = world.getChunkManager().getNoiseConfig();
        MazeStructure.Site verdict = MazeStructure.surveyFloor(gen, world, noise,
                at.getX(), at.getZ(), size, 0, MazeStructure.reliefAllowance(size),
                MazeStructure.maxWaterDepth());
        MazeStructure.Site truth = MazeStructure.surveyFloor(gen, world, noise,
                at.getX(), at.getZ(), size, 0, MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
        // The same ground, surveyed over what actually gets built: the maze plus its
        // flattened ring. The gap between the two numbers is the overhang risk.
        MazeStructure.Site built = MazeStructure.surveyFloor(gen, world, noise,
                at.getX(), at.getZ(), size, style.margin,
                MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);

        int allowed = MazeStructure.reliefAllowance(size);
        src.sendFeedback(() -> Text.literal("relief  " + truth.relief()
                        + " across the footprint, allowed " + allowed)
                .formatted(truth.relief() <= allowed ? Formatting.GREEN : Formatting.RED), false);
        src.sendFeedback(() -> Text.literal("footprint relief " + built.relief()
                        + " including the " + style.margin + "-block ring (surveyed: no)")
                .formatted(built.relief() <= allowed ? Formatting.GREEN : Formatting.YELLOW), false);
        src.sendFeedback(() -> Text.literal("water   " + truth.water()
                        + " deep at worst, allowed " + MazeStructure.maxWaterDepth()
                        + "  (" + truth.wet() + " of 25 samples wet)")
                .formatted(truth.water() <= MazeStructure.maxWaterDepth() ? Formatting.GREEN : Formatting.RED), false);
        if (verdict.ok()) {
            src.sendFeedback(() -> Text.literal("terrain ACCEPTED, floor would be y=" + verdict.floorY())
                    .formatted(Formatting.GREEN), false);
        } else {
            src.sendFeedback(() -> Text.literal("terrain REFUSED by the " + verdict.rejected()
                            + " test, at " + verdict.x() + ", " + verdict.z())
                    .formatted(Formatting.RED), false);
        }

        src.sendFeedback(() -> Text.literal("verdict " + (biomeOk && verdict.ok()
                        ? "a maze of this style and size could stand here"
                        : "no maze here"))
                .formatted(biomeOk && verdict.ok() ? Formatting.GOLD : Formatting.GRAY), false);
        return 1;
    }

    /** Blocks between two sampled sites: wide enough that 225 of them cover a real landscape. */
    private static final int SCAN_STEP = 80;

    /**
     * DEBUG: the same question as {@link #onPlacement}, asked of 225 sites around the player
     * instead of one, and answered with a distribution.
     *
     * <p>One site is an anecdote. Standing in a swamp and reading "relief 13, allowed 10" says
     * that <em>this</em> spot is too uneven; it does not say whether one swamp site in three is
     * fine or none of them are, and those two call for opposite fixes. This walks a grid, keeps
     * the sites whose biome matches, measures each one with no thresholds, and prints how many
     * would be accepted at each candidate tolerance. The tolerance then gets chosen from the
     * table rather than from my intuition, which has already been wrong once in this
     * conversation.</p>
     */
    private static int onPlacementScan(CommandContext<ServerCommandSource> ctx, String sizeId,
                                       boolean hunt, int reliefOverride, int minDrop) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.translatable("mazecraft.command.players_only"));
            return 0;
        }
        MazeStyle style = MazeStyle.fromId(StringArgumentType.getString(ctx, "style"));
        MazeSize size = MazeSize.fromId(sizeId);
        if (style == null || size == null) {
            src.sendError(Text.literal("Unknown style or size."));
            return 0;
        }
        ServerWorld world = src.getWorld();
        String id = style.isKronos() ? "maze_kronos" : "maze_" + style.id() + "_" + size.step().id();
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE)
                .get(MazeCraft.id(id));
        if (structure == null) {
            src.sendError(Text.literal("No structure " + id + " in the registry."));
            return 0;
        }

        if (running != null) {
            src.sendError(Text.literal("A scan is already running."));
            return 0;
        }
        int allowance = reliefOverride > 0 ? reliefOverride : MazeStructure.reliefAllowance(size);
        running = new Scan(src, world, structure, size, style.margin, id,
                player.getBlockPos(), hunt, allowance, minDrop);
        if (hunt) {
            src.sendFeedback(() -> Text.literal("hunting for the nearest site " + id
                            + " could stand on at relief " + allowance
                            + (allowance > MazeStructure.reliefAllowance(size)
                                    ? " but NOT at the " + MazeStructure.reliefAllowance(size)
                                      + " in force, so the site actually shows what the change buys"
                                    : "")
                            + (minDrop > 0 ? ", with a drop of at least " + minDrop : "")
                            + ", a slice per tick")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("scanning " + id + ": walking outward until "
                        + SCAN_TARGET + " sites of its biome are found, a slice per tick"
                        + " - this can take a minute for a small biome")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    /** A scan in progress: the grid is walked a slice at a time, never all in one tick. */
    private static Scan running;

    /**
     * How long a scan may hold the server thread in one tick.
     *
     * <p>Twenty milliseconds of a fifty-millisecond tick. The first version of this command had
     * no budget at all and ran the whole grid inline: 225 sites, 11 250 height queries, and a
     * dedicated server <b>28 seconds behind</b> — half the watchdog, in the one session whose
     * entire subject was not holding the tick. Measuring the problem is not a licence to
     * reproduce it.</p>
     */
    private static final long SCAN_BUDGET_NANOS = 10_000_000L;

    /**
     * Minimum real time between two slices.
     *
     * <p>Two attempts at this failed before the arithmetic was done. A per-tick budget alone
     * bounds nothing: when the server falls behind it runs catch-up ticks back to back, and the
     * budget fires on every one. A fixed 50 ms gap between slices bounds nothing either, and
     * that one is the instructive failure — it assumes a slice can stop when the budget says
     * so, but the loop can only stop <em>between sites</em>, and one site in the right biome
     * costs about 330 ms of height queries. A 10 ms budget with a 50 ms gap therefore produced
     * slices of 330 ms every 380: eighty-seven percent of the thread, which is exactly what
     * the logs showed (22.6 seconds of work in 23 seconds of wall clock).</p>
     *
     * <p>So the pacing is proportional instead of fixed: after a slice costing T, nothing runs
     * for 4 × T. Whatever the granularity, the scan gets a fifth of the thread and no more.</p>
     */
    private static final int SCAN_REST_MULTIPLIER = 4;

    /** When the last slice ended, and what it cost, for the pacing above. */
    private static long lastSlice;
    private static long lastSliceCost;

    /**
     * In-biome sites a scan tries to collect before it reports.
     *
     * <p>A target, not a grid size, because the thing being measured is the <em>terrain of a
     * style's biome</em> and biomes differ in size by two orders of magnitude. A fixed
     * 225-site square gave 55 usable samples in a forest and <b>one</b> in a cherry grove, and
     * one sample is what sent me looking at the relief tolerance for a style whose real
     * problem is that its biome is tiny. The scan now walks outward until it has enough to
     * speak, or until it has looked far enough to conclude there is nothing to find.</p>
     */
    private static final int SCAN_TARGET = 60;

    /** Hard stop: sites examined, whether or not the target was reached. */
    private static final int SCAN_MAX_SITES = 40_000;

    private static final class Scan {
        final ServerCommandSource src;
        final ServerWorld world;
        final Structure structure;
        final MazeSize size;
        final int margin;
        final String id;
        final boolean hunt;
        final int allowance;
        final int minDrop;
        final BlockPos origin;
        final java.util.List<int[]> found = new java.util.ArrayList<>();
        final java.util.ArrayDeque<int[]> pending = new java.util.ArrayDeque<>();
        int ring = -1;
        int examined;
        int inBiome;
        int wetRefusals;
        long nanos;

        /** True while there is still ground worth looking at. */
        boolean wants() {
            return found.size() < SCAN_TARGET && examined < SCAN_MAX_SITES;
        }

        /** Queues the next square ring of sites outward from the player. */
        void nextRing() {
            ring++;
            if (ring == 0) {
                pending.add(new int[]{0, 0});
                return;
            }
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) == ring) pending.add(new int[]{i, j});
                }
            }
        }

        Scan(ServerCommandSource src, ServerWorld world, Structure structure, MazeSize size,
             int margin, String id, BlockPos origin, boolean hunt, int allowance, int minDrop) {
            this.src = src;
            this.world = world;
            this.structure = structure;
            this.size = size;
            this.margin = margin;
            this.id = id;
            this.origin = origin;
            this.hunt = hunt;
            this.allowance = allowance;
            this.minDrop = minDrop;
        }
    }

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> advanceScan());
    }

    private static void advanceScan() {
        Scan scan = running;
        if (scan == null) return;
        long now = System.nanoTime();
        if (now - lastSlice < lastSliceCost * SCAN_REST_MULTIPLIER) return;
        ServerWorld world = scan.world;
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        NoiseConfig noise = world.getChunkManager().getNoiseConfig();
        // The biome source, not world.getBiome: asking the world would load a chunk per site,
        // and worldgen does not ask the world either.
        var sampler = noise.getMultiNoiseSampler();
        int sea = gen.getSeaLevel();
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (scan.wants() && System.nanoTime() < deadline) {
            if (scan.pending.isEmpty()) scan.nextRing();
            long started = System.nanoTime();
            int[] cell = scan.pending.poll();
            scan.examined++;
            int x = scan.origin.getX() + cell[0] * SCAN_STEP;
            int z = scan.origin.getZ() + cell[1] * SCAN_STEP;

            // Prefilter first, survey second. The survey is fifty height queries and the
            // prefilter is four noise samples, so asking in this order is what turns a
            // 28-second command into a few hundred milliseconds of total work: for a cherry
            // grove it skips 224 sites out of 225 before spending anything.
            if (scan.hunt) {
                if (!MazeStructure.biomeCouldMatch(scan.structure, gen.getBiomeSource(), sampler, sea, x, z)) {
                    scan.nanos += System.nanoTime() - started;
                    continue;
                }
                MazeStructure.Site real = MazeStructure.surveyFloor(gen, world, noise, x, z,
                        scan.size, 0, scan.allowance, MazeStructure.maxWaterDepth());
                var biomeHere = gen.getBiomeSource().getBiome(BiomeCoords.fromBlock(x),
                        BiomeCoords.fromBlock(real.ok() ? real.floorY() : sea),
                        BiomeCoords.fromBlock(z), sampler);
                // Asking for a LOOSER allowance than the game's means one thing only: show me
                // a site the proposal would admit and the rule in force would not. Without
                // this, the hunt hands back the first site that merely passes, which at a
                // looser allowance is usually one the current rule already accepts - it did
                // exactly that, returning a relief-8 site when asked for 14, and a relief-8
                // site says nothing whatsoever about raising the tolerance to 14.
                boolean provesSomething = scan.allowance <= MazeStructure.reliefAllowance(scan.size)
                        || real.relief() > MazeStructure.reliefAllowance(scan.size);
                if (real.ok() && provesSomething
                        && scan.structure.getValidBiomes().contains(biomeHere)) {
                    MazeStructure.Site measured = MazeStructure.surveyFloor(gen, world, noise,
                            x, z, scan.size, scan.margin,
                            MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
                    if (measured.drop() < scan.minDrop) {
                        scan.nanos += System.nanoTime() - started;
                        continue;
                    }
                    running = null;
                    int fy = real.floorY();
                    try {
                        ServerPlayerEntity who = scan.src.getPlayer();
                        if (who != null) {
                            who.requestTeleport(x + 0.5, fy + 1, z + 0.5);
                            who.fallDistance = 0.0f;
                        }
                        scan.src.sendFeedback(() -> Text.literal("found a site at " + x + ", "
                                        + fy + ", " + z + " - interior relief " + real.relief()
                                        + " of " + scan.allowance + " allowed"
                                        + "; including the built ring " + measured.relief()
                                        + "; drop below the floor at the ring " + measured.drop()
                                        + ". Run /maze debug place here.")
                                .formatted(Formatting.GOLD), false);
                    } catch (Exception e) {
                        MazeCraft.LOGGER.warn("[MazeCraft] hunt finished but could not report", e);
                    }
                    return;
                }
                scan.nanos += System.nanoTime() - started;
                continue;
            }
            if (MazeStructure.biomeCouldMatch(scan.structure, gen.getBiomeSource(), sampler, sea, x, z)) {
                MazeStructure.Site s = MazeStructure.surveyFloor(gen, world, noise, x, z,
                        scan.size, 0, MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
                boolean acceptedToday = s.relief() <= MazeStructure.reliefAllowance(scan.size)
                        && s.water() <= MazeStructure.maxWaterDepth();
                var biome = gen.getBiomeSource().getBiome(BiomeCoords.fromBlock(x),
                        BiomeCoords.fromBlock(s.floorY()), BiomeCoords.fromBlock(z), sampler);
                if (scan.structure.getValidBiomes().contains(biome)) {
                    scan.inBiome++;
                    // Kept paired, one entry per site: the decision tables ask about relief AND
                    // water of the SAME site, so separately sorted lists would answer a
                    // question nobody asked.
                    // The same twenty-five samples plus the sixteen on the built ring, so
                    // this row can only ever be lower than the one above it.
                    MazeStructure.Site wide = MazeStructure.surveyFloor(gen, world, noise,
                            x, z, scan.size, scan.margin,
                            MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
                    scan.found.add(new int[]{s.relief(), s.water(), s.wet(), wide.relief(),
                            acceptedToday ? wide.drop() : -1});
                    if (s.water() > MazeStructure.maxWaterDepth()) scan.wetRefusals++;
                }
            }
            scan.nanos += System.nanoTime() - started;
        }
        lastSlice = System.nanoTime();
        lastSliceCost = lastSlice - now;
        if (scan.hunt ? scan.examined >= SCAN_MAX_SITES : !scan.wants()) {
            running = null;
            try {
                if (scan.hunt) {
                    scan.src.sendFeedback(() -> Text.literal("no site within "
                                    + (scan.ring * SCAN_STEP) + " blocks passes the rules for "
                                    + scan.id).formatted(Formatting.GRAY), false);
                    return;
                }
                reportScan(scan);
            } catch (Exception e) {
                MazeCraft.LOGGER.warn("[MazeCraft] scan finished but could not be reported", e);
            }
        }
    }

    private static void reportScan(Scan scan) {
        ServerCommandSource src = scan.src;
        MazeSize size = scan.size;
        java.util.List<int[]> found = scan.found;
        int matched = scan.inBiome;
        int wetRefusals = scan.wetRefusals;
        long ms = scan.nanos / 1_000_000L;
        String id = scan.id;

        int sites = scan.examined;
        int blocks = scan.ring * SCAN_STEP;
        src.sendFeedback(() -> Text.literal("=== scan " + id + ": " + sites + " sites out to "
                + blocks + " blocks, " + ms + " ms of work ===").formatted(Formatting.LIGHT_PURPLE), false);
        src.sendFeedback(() -> Text.literal("biome   " + matched + " of " + sites
                + " in this style's biomes (1 in " + (matched == 0 ? sites : sites / matched) + ")")
                .formatted(Formatting.GRAY), false);
        if (matched == 0) {
            src.sendFeedback(() -> Text.literal("No site here is in the right biome - the style "
                    + "is limited by where its biome is, not by the terrain rules.")
                    .formatted(Formatting.GOLD), false);
            return;
        }

        // Percentiles over copies, so the paired list keeps its pairing.
        int[] reliefs = found.stream().mapToInt(p -> p[0]).sorted().toArray();
        int[] waters = found.stream().mapToInt(p -> p[1]).sorted().toArray();
        src.sendFeedback(() -> Text.literal("relief  min " + reliefs[0]
                        + ", p25 " + reliefs[reliefs.length / 4]
                        + ", median " + reliefs[reliefs.length / 2])
                .formatted(Formatting.GRAY), false);
        int[] wets = found.stream().mapToInt(p -> p[2]).sorted().toArray();
        int refused = wetRefusals;
        src.sendFeedback(() -> Text.literal("water   depth median " + waters[waters.length / 2]
                        + ", deepest " + waters[waters.length - 1]
                        + "; wet samples median " + wets[wets.length / 2] + " of 25"
                        + "; " + refused + " site(s) refused on water")
                .formatted(Formatting.GRAY), false);

        // Three decision tables, one per knob, each holding the other two rules as they are.
        // Which knob to turn is the whole question: a cherry grove is refused for relief and a
        // swamp for water, so one table would have answered half of it.
        //
        // The wet-samples table exists because depth may simply be the wrong discriminator for
        // a swamp. A site with twenty-four dry samples and one seven-deep pond in a corner is
        // perfectly buildable - the foundation fills it - while a site wet right across is a
        // lake. Counting wet samples separates those two; a depth cap cannot.
        int allowed = MazeStructure.reliefAllowance(size);
        src.sendFeedback(() -> Text.literal(table("relief  ", found, matched,
                new int[]{allowed, 12, 14, 16, 18, 20},
                (site, t) -> site[0] <= t
                        && site[1] <= MazeStructure.maxWaterDepth())).formatted(Formatting.GOLD), false);
        // The row alpha.10 turns on: the same sites judged over the footprint that is
        // actually built, ring included. The drop from the row above is the price of never
        // leaving a maze hanging in the air.
        src.sendFeedback(() -> Text.literal(table("built   ", found, matched,
                new int[]{allowed, 12, 14, 16, 18, 20},
                (site, t) -> site[3] <= t
                        && site[1] <= MazeStructure.maxWaterDepth())).formatted(Formatting.AQUA), false);
        // THE question, asked directly instead of through a tolerance table: of the sites
        // generation accepts as it stands, how many would have ground further below their
        // floor than the foundation can reach? Those, and only those, hang in the air.
        int[] drops = found.stream().mapToInt(p -> p[4]).filter(d -> d >= 0).sorted().toArray();
        int hanging = (int) java.util.Arrays.stream(drops).filter(d -> d > MazePiece.FOUNDATION_DEPTH).count();
        if (drops.length == 0) {
            src.sendFeedback(() -> Text.literal("overhang  no site here is accepted as it stands")
                    .formatted(Formatting.GRAY), false);
        } else {
            src.sendFeedback(() -> Text.literal("overhang  " + hanging + " of " + drops.length
                            + " accepted sites drop more than " + MazePiece.FOUNDATION_DEPTH
                            + " below their floor at the ring (median " + drops[drops.length / 2]
                            + ", worst " + drops[drops.length - 1] + ")")
                    .formatted(hanging == 0 ? Formatting.GREEN : Formatting.RED), false);
        }
        src.sendFeedback(() -> Text.literal(table("depth   ", found, matched,
                new int[]{MazeStructure.maxWaterDepth(), 4, 6, 8, 12},
                (site, t) -> site[0] <= allowed
                        && site[1] <= t)).formatted(Formatting.GOLD), false);
        // ASCII: these lines get pasted out of the client log, where a Windows code page
        // turns anything else into mojibake.
        src.sendFeedback(() -> Text.literal(table("wet max ", found, matched,
                new int[]{0, 4, 8, 12, 18, 25},
                (site, t) -> site[0] <= allowed
                        && site[2] <= t)).formatted(Formatting.GOLD), false);
    }

    /** One decision row: how many of the surveyed sites each candidate value would admit. */
    private interface Rule {
        boolean accepts(int[] site, int value);
    }

    private static String table(String label, java.util.List<int[]> sites, int total,
                                int[] values, Rule rule) {
        StringBuilder out = new StringBuilder(label);
        for (int value : values) {
            int pass = 0;
            for (int[] site : sites) {
                if (rule.accepts(site, value)) pass++;
            }
            out.append("  ").append(value).append(": ").append(pass * 100 / total).append('%');
        }
        return out.toString();
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

        // Say, loudly, whether generation would ever choose this spot.
        //
        // This command forces a maze at the player's feet and runs no placement test at all,
        // which is the point of it — but it means what you are looking at is not necessarily
        // something the game can produce. A maze forced onto ground with a relief of 49, five
        // times the tolerance, came out as a slab hanging in the air, and that screenshot was
        // taken as evidence against a relief setting it had never exercised. A debug tool that
        // can be mistaken for the real thing has to say so itself.
        MazeStructure.Site check = MazeStructure.surveyFloor(
                world.getChunkManager().getChunkGenerator(), world,
                world.getChunkManager().getNoiseConfig(), origin.getX(), origin.getZ(), size, 0,
                MazeStructure.reliefAllowance(size), MazeStructure.maxWaterDepth());
        MazeStructure.Site measured = MazeStructure.surveyFloor(
                world.getChunkManager().getChunkGenerator(), world,
                world.getChunkManager().getNoiseConfig(), origin.getX(), origin.getZ(), size,
                style.margin, MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
        if (check.ok()) {
            src.sendFeedback(() -> Text.literal("[DEBUG] this site PASSES the placement rules"
                            + " (interior relief " + check.relief() + " of "
                            + MazeStructure.reliefAllowance(size) + " allowed; including the built"
                            + " ring " + measured.relief() + "; drop below the floor at the ring "
                            + measured.drop() + ")")
                    .formatted(Formatting.GREEN), false);
        } else {
            src.sendFeedback(() -> Text.literal("[DEBUG] WARNING: generation would REFUSE this site"
                            + " (" + check.rejected() + " test; relief " + measured.relief()
                            + " for an allowance of " + MazeStructure.reliefAllowance(size)
                            + "). What you are about to see is NOT something the game can produce.")
                    .formatted(Formatting.RED), false);
        }

        // Sit it on the floor generation would have chosen when the site is a real one, so a
        // legitimate test looks like the real thing rather than like the player's feet.
        int floorY = check.ok() ? check.floorY() : origin.getY() - 1;

        MazePiece piece = new MazePiece(style, size, seed, origin.getX(), floorY, origin.getZ(), -1);
        BlockBox box = piece.getBoundingBox();
        piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(),
                Random.create(seed), box, new ChunkPos(origin), origin);

        lastPlaced = piece;
        src.sendFeedback(() -> Text.literal("[DEBUG] Placed " + size.id() + " " + style.id() + " maze ("
                + size.span() + "x" + size.span() + ") centered on " + origin.getX() + " " + floorY + " " + origin.getZ()
                + " - seed " + seed).formatted(Formatting.LIGHT_PURPLE), true);
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
                + " maze - " + (solved ? "CONQUERED" : "locked")
                + " - chest " + chest.toShortString() + " - entrance " + entrance.toShortString())
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
                    + " - lever at " + maze.leverPos(k).toShortString())
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
                    + ") - only colossal mazes have one, Kronos excepted."));
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
                + " - door " + door).formatted(Formatting.LIGHT_PURPLE), false);

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
                    + " maze at " + maze.centerPos().toShortString() + " - entrance "
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
                + " - " + looked + " large mazes looked at, " + empty + " probes found nothing"
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
