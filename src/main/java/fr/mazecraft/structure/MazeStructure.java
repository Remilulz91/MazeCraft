package fr.mazecraft.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.mazecraft.MazeCraft;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The maze structure type ({@code mazecraft:maze}).
 *
 * Placement (which chunks, which biomes, how rare) is data-driven:
 * <ul>
 *   <li>{@code data/mazecraft/worldgen/structure/*.json} — one entry per style + biome tag;</li>
 *   <li>{@code data/mazecraft/worldgen/structure_set/mazes.json} — spacing / separation.</li>
 * </ul>
 * Because it is a regular structure, mazes also appear in not-yet-generated chunks of
 * existing worlds, and {@code /locate structure} works.
 */
public class MazeStructure extends Structure {

    /**
     * JSON: the usual structure fields, plus
     * {@code "style": "hedge" | "desert" | "snow" | "jungle"...} and
     * {@code "size": "small" | "medium" | "large"}.
     *
     * Since 0.8.0 the size is fixed by the structure instead of being rolled: the progression
     * (small → medium → large, per style) needs each step to be a findable structure of its own.
     * Legacy {@code maze_<style>.json} entries have no size field and default to medium; they are
     * no longer referenced by any structure set, they only keep pre-0.8.0 worlds loading.
     */
    public static final MapCodec<MazeStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Structure.configCodecBuilder(instance),
            Codec.STRING.optionalFieldOf("style", "hedge").forGetter(s -> s.style.id()),
            Codec.STRING.optionalFieldOf("size", "medium").forGetter(s -> s.size.id())
    ).apply(instance, MazeStructure::new));

    private final MazeStyle style;
    private final MazeSize size;

    /** Floor height range of enclosed (Nether) mazes. Lava sea is at Y=31, bedrock roof from Y=123. */
    private static final int NETHER_MIN_FLOOR = 40;
    private static final int NETHER_MAX_FLOOR = 80;

    /**
     * Floor height range of the Labyrinth of Kronos: deep under the Overworld, clear of the
     * bedrock floor (Y -64..-59) and below where players dig by habit.
     */
    // Raised from -50 in 1.0.0-alpha.3: the hoard of Asterion is dug ten blocks under the
    // arena floor, and at -50 its own floor landed at -60 — inside the bedrock layer, which
    // runs to -59. The vault is still deep in the deepslate.
    private static final int KRONOS_MIN_FLOOR = -44;
    private static final int KRONOS_MAX_FLOOR = -36;

    public MazeStructure(Structure.Config config, String styleId, String sizeId) {
        super(config);
        MazeStyle parsedStyle = MazeStyle.fromId(styleId);
        if (parsedStyle == null) {
            MazeCraft.LOGGER.warn("[MazeCraft] Unknown maze style '{}' in worldgen JSON, using hedge", styleId);
            parsedStyle = MazeStyle.HEDGE;
        }
        this.style = parsedStyle;

        MazeSize parsedSize = MazeSize.fromId(sizeId);
        // Colossal is normally a rare upgrade of a large maze rather than a size a structure may
        // ask for — except for Kronos, which is colossal by definition.
        if (parsedSize == null || (parsedSize == MazeSize.COLOSSAL && !this.style.isKronos())) {
            MazeCraft.LOGGER.warn("[MazeCraft] Invalid maze size '{}' in worldgen JSON, using medium", sizeId);
            parsedSize = MazeSize.MEDIUM;
        }
        this.size = parsedSize;
    }

    public MazeStyle getStyle() {
        return style;
    }

    /** The progression step this structure generates (never COLOSSAL — that is a LARGE variant). */
    public MazeSize getSize() {
        return size;
    }

    /**
     * How far a maze must keep from the origin.
     *
     * <p>Honest about what this is: Minecraft does not hand worldgen the spawn point — it is
     * chosen afterwards, on suitable ground near the origin — so this cannot be exact. It
     * clears a square around (0, 0) wide enough to cover where the spawn is normally picked.
     * It makes landing in a maze at world spawn very unlikely, not impossible.</p>
     */
    private static final int SPAWN_CLEARANCE = 160;

    /** Would a maze placed here reach into the cleared square around the origin? */
    private boolean coversWorldSpawn(int centerX, int centerZ) {
        int reach = size.span() / 2 + MazeStyle.MAX_MARGIN + SPAWN_CLEARANCE;
        return Math.abs(centerX) < reach && Math.abs(centerZ) < reach;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunkPos = context.chunkPos();
        long mazeSeed = context.random().nextLong();
        int centerX = chunkPos.getCenterX();
        int centerZ = chunkPos.getCenterZ();

        // Nothing on top of the world's spawn. A maze that covers it drops a new player inside
        // a structure they may not even be allowed to enter — the barrier shoves them straight
        // back out, and again on every death. Kronos is exempt: it is forty blocks underground,
        // nobody spawns inside it, and it is rare enough that losing a site would cost more
        // than it saves.
        if (!style.isKronos() && coversWorldSpawn(centerX, centerZ)) return Optional.empty();

        // Wrong biome? Stop here, before anything expensive.
        //
        // Vanilla checks a structure's biome AFTER getStructurePosition has returned, which is
        // fine for a structure that costs one height query to place and ruinous for this one:
        // every candidate site ran the full avoidance sweep and a 25-point terrain survey, and
        // was then thrown away because a hedge maze cannot stand in a desert. Fifteen styles
        // out of sixteen paid in full for an answer that was already known.
        if (!biomeCouldMatch(context, centerX, centerZ)) return Optional.empty();

        // The size is fixed by the structure. It is never downgraded when the terrain is poor:
        // a "medium" structure that quietly generated a small maze would hand the player the
        // wrong progression step. A spot that doesn't fit simply gets no maze.
        if (style.isKronos()) {
            // One of a kind, and buried: the whole point is that it is not stumbled upon. It
            // must also keep well clear of ancient cities, which share its depth — the usual
            // avoidance ignores them because a surface maze never meets one.
            if (StructureAvoidance.isNearOtherStructure(context, size, style)) return Optional.empty();
            OptionalInt floorY = findBuriedFloorY(context, centerX, centerZ, size,
                    KRONOS_MIN_FLOOR, KRONOS_MAX_FLOOR);
            if (floorY.isEmpty()) return Optional.empty();
            int y = floorY.getAsInt();
            int entrance = context.random().nextInt(4);
            MazePiece vault = new MazePiece(style, size, mazeSeed, centerX, y, centerZ, entrance);
            // Clear of the bounding box: inside it, the repair pass rebuilds the roof over the
            // margin ring and plugs the shaft. Two blocks past the box puts the foot of the
            // shaft right against the doorway the maze already leaves in its outer wall.
            BlockPos shaftFoot = vault.entranceOutsidePos(vault.getMargin() + 2);
            Direction toVault = Direction.getFacing(
                    centerX - shaftFoot.getX(), 0, centerZ - shaftFoot.getZ());
            int surface = context.chunkGenerator().getHeight(shaftFoot.getX(), shaftFoot.getZ(),
                    Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
            return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector -> {
                collector.addPiece(vault);
                collector.addPiece(new KronosGatePiece(shaftFoot, surface, toVault));
            }));
        }

        // A style reached through a building on the surface. The one generic branch the landmark
        // package needs: everything particular to the building is behind `landmark`, so the next
        // one is a new file in fr.mazecraft.landmark and nothing changed here.
        //
        // Same shape as the Kronos branch above, and for the same reason: a buried maze plus a
        // surface piece that is the way into it. What differs is which end is the fixed point.
        // Kronos puts its shaft against the doorway in the maze's outer wall; here the stairs
        // come out in the middle of the maze, so the building is placed around the plaza rather
        // than beside it.
        fr.mazecraft.landmark.Landmark landmark = fr.mazecraft.landmark.Landmarks.of(style);
        if (landmark != null) {
            if (StructureAvoidance.isNearOtherStructure(context, size, style)) return Optional.empty();
            // Ground for the building, judged on the building's own footprint — not the maze's.
            Site pad = landmark.site(context.chunkGenerator(), context.world(),
                    context.noiseConfig(), centerX, centerZ);
            if (!pad.ok()) return Optional.empty();

            // One large in COLOSSAL_CHANCE becomes a colossal one, exactly as it does for the
            // surface styles. A colossal is a VARIANT of the large step, not a step of its own
            // (MazeSize.step maps it to LARGE), so falling back to large when it does not fit
            // costs the player nothing — which is why this one downgrade is allowed and a
            // large quietly becoming a medium is not.
            //
            // Expect it to be rare to the point of myth for now. A colossal footprint is 221
            // blocks across, and the ceiling has to clear the lowest ground anywhere in it;
            // over that much terrain there is almost always a dip. The ceiling rule is the
            // thing to change if colossal pagodas should really exist.
            MazeSize actual = size;
            if (size == MazeSize.LARGE && context.random().nextInt(MazeSize.COLOSSAL_CHANCE) == 0
                    && !StructureAvoidance.isNearOtherStructure(context, MazeSize.COLOSSAL, style)
                    && landmark.floorY(context.chunkGenerator(), context.world(),
                            context.noiseConfig(), centerX, centerZ, MazeSize.COLOSSAL,
                            pad.floorY()).isPresent()) {
                actual = MazeSize.COLOSSAL;
            }
            final MazeSize chosen = actual;

            // How deep the maze goes, which the terrain decides rather than a constant.
            OptionalInt floorY = landmark.floorY(context.chunkGenerator(), context.world(),
                    context.noiseConfig(), centerX, centerZ, chosen, pad.floorY());
            if (floorY.isEmpty()) return Optional.empty();
            int y = floorY.getAsInt();
            int entrance = context.random().nextInt(4);
            Direction facing = Direction.fromHorizontal(context.random().nextInt(4));
            MazePiece maze = new MazePiece(style, chosen, mazeSeed, centerX, y, centerZ, entrance);
            // The maze first and the building second, because within one chunk the pieces
            // generate in the order they are added here, and the shaft has to be cut through the
            // maze's roof after that roof has been laid. The other way round, the roof plugs the
            // staircase and the only way in is sealed by the thing it leads to.
            return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector -> {
                collector.addPiece(maze);
                collector.addPiece(new fr.mazecraft.landmark.LandmarkPiece(landmark,
                        new BlockPos(centerX, y, centerZ), pad.floorY(), y, facing));
            }));
        }

        if (style.isNether()) {
            // Nether: buried in the rock like a fortress, at the height where the rock is the
            // most solid (never hanging over the lava sea). No colossal mazes down here.
            //
            // isNether(), not enclosed: this branch is about a lava sea at Y=31 and a bedrock
            // roof from Y=123, which is a fact about the Nether, not about having a roof.
            if (StructureAvoidance.isNearOtherStructure(context, size, style)) return Optional.empty();
            OptionalInt floorY = findBuriedFloorY(context, centerX, centerZ, size,
                    NETHER_MIN_FLOOR, NETHER_MAX_FLOOR);
            if (floorY.isEmpty()) return Optional.empty();
            int y = floorY.getAsInt();
            int entrance = mostOpenSide(context, centerX, centerZ, size, y);
            return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector ->
                    collector.addPiece(new MazePiece(style, size, mazeSeed, centerX, y, centerZ, entrance))));
        }

        // Overworld / End: one large maze in COLOSSAL_CHANCE becomes a colossal one. It is the
        // same progression step, just bigger and richer — so it needs the flat area to match.
        MazeSize actual = size;
        if (size == MazeSize.LARGE && context.random().nextInt(MazeSize.COLOSSAL_CHANCE) == 0
                && !StructureAvoidance.isNearOtherStructure(context, MazeSize.COLOSSAL, style)
                && findFloorY(context, centerX, centerZ, MazeSize.COLOSSAL).isPresent()) {
            actual = MazeSize.COLOSSAL;
        }

        if (StructureAvoidance.isNearOtherStructure(context, actual, style)) return Optional.empty();
        OptionalInt floorY = findFloorY(context, centerX, centerZ, actual);
        if (floorY.isEmpty()) return Optional.empty();
        final MazeSize finalSize = actual;
        int y = floorY.getAsInt();
        int entrance = bestEntranceSide(context, centerX, centerZ, finalSize, y, style);
        return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector ->
                collector.addPiece(new MazePiece(style, finalSize, mazeSeed, centerX, y, centerZ, entrance))));
    }

    /**
     * Could this style's biome be the one here?
     *
     * <p>Sampled at several fixed heights rather than at the terrain surface, for the same
     * reason {@link StructureAvoidance} does: asking a noise generator for the real surface
     * height builds an entire NoiseChunk, and the whole point of this check is to be cheap
     * enough to run before the expensive work.</p>
     *
     * <p>Deliberately generous — it says yes if <em>any</em> of the sampled heights matches.
     * A false yes only costs the work that used to be done unconditionally; a false no would
     * silently delete a maze site, so the check is built so that the heights it misses are
     * ones no maze could stand on anyway.</p>
     */
    private boolean biomeCouldMatch(Context context, int x, int z) {
        return biomeCouldMatch(this, context.biomeSource(),
                context.noiseConfig().getMultiNoiseSampler(),
                context.chunkGenerator().getSeaLevel(), x, z);
    }

    /**
     * The same prefilter, callable from outside with any structure — the debug scan uses it to
     * skip the terrain survey where no maze of this style could stand anyway, which is most of
     * the map and, for a small biome like a cherry grove, all but one site in two hundred.
     *
     * <p>Shared rather than copied on purpose: a scan that filtered differently from worldgen
     * would be measuring a rule the mod does not have.</p>
     */
    public static boolean biomeCouldMatch(Structure structure,
                                          net.minecraft.world.biome.source.BiomeSource source,
                                          net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler,
                                          int seaLevel, int x, int z) {
        boolean kronos = structure instanceof MazeStructure maze && maze.getStyle().isKronos();
        int[] heights = kronos
                ? new int[]{KRONOS_MIN_FLOOR, KRONOS_MAX_FLOOR, seaLevel}
                : new int[]{seaLevel, seaLevel + 40, seaLevel + 80, seaLevel + 120};
        for (int y : heights) {
            RegistryEntry<Biome> biome = source.getBiome(
                    BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
                    sampler);
            if (structure.getValidBiomes().contains(biome)) return true;
        }
        return false;
    }

    /** The worldgen view of {@link #surveyFloor}: the floor Y, or empty when the site is refused. */
    private static OptionalInt findFloorY(Context context, int centerX, int centerZ, MazeSize size) {
        // ringMargin 0: the survey still stops at the maze's own half-span, which is what
        // every world generated so far was built on. The flattened ring reaches `margin`
        // blocks further out and is NOT surveyed, which is how a maze ends up with a shelf of
        // ground hanging in the air on steep terrain. Widening this is 1.0.0-alpha.10's
        // subject; it makes the test stricter for every style at once, so the figure comes
        // from `/maze debug placement ... scan`, which reports both spans side by side,
        // before it is changed here and not after.
        Site site = surveyFloor(context.chunkGenerator(), context.world(), context.noiseConfig(),
                centerX, centerZ, size, 0, reliefAllowance(size), MAX_WATER_DEPTH);
        return site.ok() ? OptionalInt.of(site.floorY()) : OptionalInt.empty();
    }

    /**
     * Hillside a maze of this size may cut, in blocks.
     *
     * <p>Public because {@code /maze debug placement} must report the rule the game uses, not
     * a second copy of it that agrees today. It does not vary by style, and 1.0.0-alpha.9 is
     * the release that established it should not.</p>
     *
     * <p>Cherry grove mazes are roughly ninety times rarer than hedge ones — one site in 205
     * is in the biome and only 3 % of those are flat enough — so the tolerance was raised for
     * that style alone, by four blocks, capped below the figure 0.8.0 had rejected. Two
     * independent checks said it was safe: the height of the exposed cut face, and its
     * steepness relative to the platform. Built in game it produced a slab of ground
     * <b>hanging in the air</b> off the side of a hill.</p>
     *
     * <p>Both checks were blind to the same thing. {@link #surveyFloor} samples the terrain out
     * to the maze's own half-span, while the build lays its flattened ring {@code margin}
     * blocks further out again, and the foundation fills only
     * {@code MazePiece.FOUNDATION_DEPTH} blocks downward. On terraced ground all twenty-five
     * samples can land on terrace tops, report a modest relief, and miss the cliff that starts
     * just past the last of them. Raising this does not buy steeper mazes; it buys floating
     * ones, in every style. Until the survey covers the footprint that actually gets built,
     * this number is not the knob it looks like.</p>
     */
    public static int reliefAllowance(MazeSize size) {
        return Math.min(size.maxRelief(), MAX_RELIEF);
    }

    /**
     * The tallest cut face allowed anywhere, whatever the style asks for.
     *
     * <p>16, because 0.8.0 tried 18 on large mazes and it "cuts a hillside into a visible
     * escarpment" — that sentence is about the absolute height of the exposed face, which is
     * what the eye reads, and 16 is the figure colossal mazes already use without complaint.
     * A per-style bonus can make a style more tolerant; it cannot push the mod past a limit
     * that was set by looking at the result in game.</p>
     */
    public static final int MAX_RELIEF = 16;

    /**
     * What a terrain survey found, and which test refused the site.
     *
     * <p>The reason exists because the alternative is guessing, and guessing has already been
     * wrong once here: two styles out of sixteen turned out to be nearly unfindable on a
     * server, the obvious explanation was "a swamp is wet", and the first thing this record
     * reported was that both were refused by the <em>relief</em> test with the water never
     * reached. A rule that can be asked why it said no is worth more than a rule I am confident
     * about.</p>
     *
     * <p>Note that {@code relief} and {@code water} are the values measured <b>up to the point
     * the survey stopped</b>. When a threshold is exceeded the survey returns there and then,
     * so the figure is a lower bound on the site's real relief, not its full-footprint value.
     * To measure rather than judge, call {@link #surveyFloor} with thresholds nothing can
     * exceed; it then walks all twenty-five samples and both figures are exact.</p>
     *
     * @param wet      how many of the samples stood in any water at all
     * @param rejected the test that refused, or null when the site is good
     * @param lowest   the lowest surface among the samples taken, which is the number a buried
     *                 maze turns on: its roof has to pass under the lowest ground anywhere over
     *                 its footprint, not under the ground at its middle. Exact only on a survey
     *                 that ran to the end — see the note above about early returns.
     */
    public record Site(int relief, int water, int wet, int drop, int floorY, String rejected,
                       int x, int z, int lowest) {
        public boolean ok() {
            return rejected == null;
        }

        /**
         * Does {@link #floorY} mean anything?
         *
         * <p>Only on a survey that ran to the end. The floor is the average of all twenty-five
         * samples, so a survey that stopped at the sample which broke a rule has no floor to
         * report and carries <b>0</b> — a sentinel that looks exactly like a height, which is
         * how a refused site put a pagoda at y = 0, in a cavern a hundred blocks below the maze
         * it was supposed to lead down to. Anything that wants a height from a survey that may
         * have been refused should survey again with {@link #NO_LIMIT} thresholds instead of
         * reading this.</p>
         */
        public boolean hasFloor() {
            return rejected == null;
        }

        /** The highest surface among the samples taken. */
        public int highest() {
            return lowest + relief;
        }
    }

    /**
     * Samples the terrain on a 5×5 grid over the footprint.
     *
     * <p>This is the single source of truth. Worldgen reaches it through {@link #findFloorY}
     * with the real thresholds; the debug command calls it with the live world's generator,
     * either with the real thresholds (to ask what generation decided) or with thresholds
     * nothing can exceed (to measure what the ground actually is). One code path, so what the
     * command prints cannot drift away from what the mod does.</p>
     *
     * @param maxRelief greatest height difference tolerated across the footprint
     * @param maxWater  deepest water tolerated at any sample
     */
    public static Site surveyFloor(ChunkGenerator gen, net.minecraft.world.HeightLimitView world,
                                   net.minecraft.world.gen.noise.NoiseConfig noise,
                                   int centerX, int centerZ, MazeSize size, int ringMargin,
                                   int maxRelief, int maxWater) {
        return surveyFloor(gen, world, noise, centerX, centerZ, size.span() / 2, ringMargin,
                maxRelief, maxWater);
    }

    /**
     * The same survey over an arbitrary footprint, for something that is not a maze.
     *
     * <p>A surface landmark is judged on <b>its own</b> footprint, not the maze's. The cherry
     * pagoda is 26 by 25; the medium maze that goes under it is 101 by 101. Asking for flat
     * ground across a hundred blocks when all that has to stand up is a twenty-six block
     * building is what made the cherry style cover 0.022 % of the world — the maze is going
     * underground precisely so that it stops being the thing the terrain has to accommodate.</p>
     *
     * @param half half-width of the footprint in blocks
     */
    public static Site surveyFloor(ChunkGenerator gen, net.minecraft.world.HeightLimitView world,
                                   net.minecraft.world.gen.noise.NoiseConfig noise,
                                   int centerX, int centerZ, int half, int ringMargin,
                                   int maxRelief, int maxWater) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, n = 0, deepest = 0, wet = 0;
        for (int[] offset : SAMPLE_ORDER) {
            {
                int x = centerX + offset[0] * half / 2;
                int z = centerZ + offset[1] * half / 2;
                int surface = gen.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                // Bail the moment the ground is too uneven, instead of finishing the survey
                // and checking afterwards. Same answer, fewer samples — and a sample here is a
                // getHeight, which on a noise generator builds a whole NoiseChunk. Most sites
                // are rejected, and a rejected site now costs a handful of them instead of
                // fifty, which is what makes a compass search survivable on a server.
                if (max - min > maxRelief) {
                    return new Site(max - min, deepest, wet, 0, 0, "relief", x, z, min);
                }
                // The water test needs a second NoiseChunk, so it waits until the relief test
                // has had its say: a site rejected for relief — which is most of them — now
                // costs one height query at the sample that rejected it instead of two. Both
                // tests answer "no site here", so which one speaks first changes nothing.
                //
                // At this stage of generation nothing but a fluid can stand between the two
                // heightmaps: trees and plants are not placed yet, so the gap IS the water
                // depth, in blocks.
                int ground = gen.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
                deepest = Math.max(deepest, surface - ground);
                if (surface > ground) wet++;
                if (surface - ground > maxWater) {
                    return new Site(max - min, deepest, wet, 0, 0, "water", x, z, min);
                }
                sum += surface;
                n++;
            }
        }
        // getHeight returns the first air block → the floor replaces the surface block below it
        int floorY = Math.round((float) sum / n) - 1;

        // The ring the build actually flattens, which the 5×5 above never looks at.
        //
        // This is NOT the same thing as stretching the 5×5 further out. The first attempt did
        // that, and it was not a stricter rule but a coarser one: the same twenty-five points
        // spread wider thin out the middle, so they can miss a bump the narrow survey caught.
        // The measurement said so out loud — desert accepted MORE sites at tolerance 18 under
        // the "wider" rule than under the narrow one, which is impossible for a rule that only
        // adds ground to look at. Sixteen points on the perimeter, on top of the twenty-five,
        // can only ever refuse more.
        //
        // The ring feeds the relief and water tests only, never the floor height, which stays
        // the average of the same twenty-five samples it has always been.
        //
        // It also measures `drop`: how far the solid ground falls below that floor out at the
        // ring. That, not the relief spread, is the number the overhang question turns on — a
        // maze hangs in the air exactly when the drop exceeds MazePiece.FOUNDATION_DEPTH,
        // because the foundation fills that far down and then stops.
        int drop = 0;
        if (ringMargin > 0) {
            int edge = half + ringMargin;
            for (int k = 0; k < 16; k++) {
                int[] p = ringPoint(k, edge);
                int x = centerX + p[0];
                int z = centerZ + p[1];
                int surface = gen.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                if (max - min > maxRelief) {
                    return new Site(max - min, deepest, wet, drop, 0, "relief (ring)", x, z, min);
                }
                int ground = gen.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
                deepest = Math.max(deepest, surface - ground);
                if (surface > ground) wet++;
                // ground is the first free y, so the solid top is one below it.
                drop = Math.max(drop, floorY - (ground - 1));
                if (surface - ground > maxWater) {
                    return new Site(max - min, deepest, wet, drop, 0, "water (ring)", x, z, min);
                }
            }
        }

        return new Site(max - min, deepest, wet, drop, floorY, null, 0, 0, min);
    }

    /**
     * Point {@code k} of the sixteen on the perimeter of a square of half-width {@code edge}:
     * the four corners first, then the midpoints, then the rest, so a slope is caught early.
     */
    private static int[] ringPoint(int k, int edge) {
        int half = edge / 2;
        int[][] ring = {
                {-edge, -edge}, {edge, edge}, {edge, -edge}, {-edge, edge},
                {0, -edge}, {0, edge}, {-edge, 0}, {edge, 0},
                {-half, -edge}, {half, -edge}, {-half, edge}, {half, edge},
                {-edge, -half}, {-edge, half}, {edge, -half}, {edge, half},
        };
        return ring[k];
    }


    /**
     * The order the 5×5 grid is sampled in: the four corners first, then the edge midpoints,
     * then the middle, then the rest.
     *
     * <p>Row by row, the first samples sit along one edge of the footprint and say little about
     * its overall slope, so a hillside is often only caught several height queries in. The
     * corners bracket the footprint at once. Measured over 200 000 simulated sites: <b>40 %
     * fewer height queries</b>, 21.5 down to 13.0 per site, which at the 2.5 ms a query costs
     * on a dedicated server is most of a second off the worst case.</p>
     *
     * <p>The first version of that measurement said 0.3 % and nearly got this reverted as
     * pointless. It generated each sample height independently, and terrain is not independent:
     * on uncorrelated noise five samples along one edge already span the full range, so row
     * order loses nothing. Re-run on sloped, smoothly varying ground — which is what a rejected
     * site actually looks like — the gain appears. A speed-up measured on the wrong model is
     * indistinguishable from no speed-up.</p>
     *
     * <p>This changes cost, never outcome. A site is refused when the relief or the water
     * exceeds the threshold <em>somewhere</em> in the footprint, which does not depend on the
     * order the samples are visited in; and a site that is accepted has been fully surveyed, so
     * its floor — the average of all twenty-five — is identical either way. Checked rather than
     * asserted: over 200 000 random terrains, both orders returned the same verdict and the
     * same floor every time, 0 mismatches. This decides where every maze in every world lands,
     * so "obviously equivalent" was not good enough.</p>
     */
    private static final int[][] SAMPLE_ORDER = {
            {-2, -2}, {2, 2}, {2, -2}, {-2, 2},
            {0, -2}, {0, 2}, {-2, 0}, {2, 0},
            {0, 0},
            {-1, -2}, {1, -2}, {-1, 2}, {1, 2},
            {-2, -1}, {-2, 1}, {2, -1}, {2, 1},
            {-1, -1}, {1, 1}, {-1, 1}, {1, -1},
            {0, -1}, {0, 1}, {-1, 0}, {1, 0},
    };

    /** Thresholds nothing can exceed: the survey measures the ground instead of judging it. */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    /**
     * Water a site may stand in, in blocks, before it is refused.
     *
     * <p><b>Zero, and deliberately back at zero.</b> It was briefly 2, on my reasoning that a
     * swamp is wet and that this was why swamp mazes could not be found. Then the scan measured
     * it, and the reasoning did not survive:</p>
     *
     * <pre>
     *   style   biome area   terrain accepted   product   found by the compass
     *   hedge      24%             9%            2.2%     yes, at 4 152 blocks
     *   desert      6%            21%            1.3%     yes, at 2 330 blocks
     *   swamp      22%             6%            1.3%     no
     * </pre>
     *
     * <p>At this threshold a swamp site is as likely to carry a maze as a desert site, and
     * deserts are found without trouble. The water rule was never what made swamp mazes
     * unfindable — a blind ring in the compass's own sweep geometry was, and that is fixed in
     * {@code StructureSearch}. Raising this to 8 would have tripled swamp mazes, hidden the
     * real bug, and let every maze in the mod sit across a river.</p>
     *
     * <p>It stays a parameter rather than becoming {@code surface != ground} again, because
     * {@code /maze debug placement} needs to vary it to measure anything. But the shipped value
     * is the one the game has always had, and it will take a measurement, not an argument, to
     * change it.</p>
     */
    private static final int MAX_WATER_DEPTH = 0;

    /** The water tolerance, for the debug command that reports what the rules allow. */
    public static int maxWaterDepth() {
        return MAX_WATER_DEPTH;
    }

    /**
     * Picks the side whose surrounding terrain is closest to the maze floor, so the entrance
     * opens onto walkable ground instead of a cliff or a slope. Water counts as very bad.
     */
    private static int bestEntranceSide(Context context, int centerX, int centerZ, MazeSize size, int floorY, MazeStyle style) {
        ChunkGenerator gen = context.chunkGenerator();
        int half = size.span() / 2;
        int dist = half + style.margin + 3; // just past the flattened ring
        int[][] sideDirs = {
                {0, -1}, // NORTH
                {0, 1},  // SOUTH
                {-1, 0}, // WEST
                {1, 0}   // EAST
        };
        int best = 0;
        long bestScore = Long.MAX_VALUE;
        for (int side = 0; side < 4; side++) {
            long score = 0;
            for (int k = -2; k <= 2; k++) {
                int along = k * half / 3;
                int x = centerX + sideDirs[side][0] * dist + (sideDirs[side][0] == 0 ? along : 0);
                int z = centerZ + sideDirs[side][1] * dist + (sideDirs[side][1] == 0 ? along : 0);
                int surface = gen.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                int ground = gen.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
                score += Math.abs((surface - 1) - floorY);
                if (surface != ground) score += 50; // water in front of the entrance
            }
            if (score < bestScore) {
                bestScore = score;
                best = side;
            }
        }
        return best;
    }

    /** Minimum share of solid rock around an enclosed maze (samples below, inside and above it). */
    private static final float MIN_SOLID_RATIO = 0.6f;

    /**
     * Enclosed mazes: samples the terrain on a 5×5 grid over the footprint and returns the floor
     * height (between NETHER_MIN_FLOOR and NETHER_MAX_FLOOR) where the maze would be the most
     * buried in solid rock. Rejects heights with lava around the maze, and returns empty if even
     * the best height is not solid enough (the maze would float in a cavern).
     */
    private static OptionalInt findBuriedFloorY(Context context, int centerX, int centerZ, MazeSize size,
                                                int minFloor, int maxFloor) {
        var columns = burialColumns(context.chunkGenerator(), context.world(), context.noiseConfig(),
                centerX, centerZ, size);
        int bestY = -1;
        float bestRatio = -1f;
        int offset = context.random().nextInt(2); // vary the parity between mazes
        for (int y = minFloor + offset; y <= maxFloor; y += 2) {
            if (netherFluid(columns, y)) continue;
            float ratio = burialSolid(columns, y);
            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestY = y;
            }
        }
        return bestRatio >= MIN_SOLID_RATIO ? OptionalInt.of(bestY) : OptionalInt.empty();
    }

    /**
     * The 25 column samples the burial tests read, over the footprint plus the margin ring.
     *
     * <p>A column sample is one NoiseChunk column, so these twenty-five are the whole cost of a
     * burial test however many heights it then scores: the loop over candidate floors reads
     * them again, it does not sample again.</p>
     */
    private static java.util.List<net.minecraft.world.gen.chunk.VerticalBlockSample> burialColumns(
            ChunkGenerator gen, net.minecraft.world.HeightLimitView world,
            net.minecraft.world.gen.noise.NoiseConfig noise, int centerX, int centerZ, MazeSize size) {
        int half = size.span() / 2 + 4;
        var columns = new java.util.ArrayList<net.minecraft.world.gen.chunk.VerticalBlockSample>();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                columns.add(gen.getColumnSample(centerX + i * half / 2, centerZ + j * half / 2,
                        world, noise));
            }
        }
        return columns;
    }

    /**
     * Share of solid rock around a floor laid at {@code y}, or -1 when a fluid is in the way.
     *
     * <p>The probes are relative to the floor and straddle it: below it, at walking height, and
     * above the roof. No fluid is allowed at any of them — lava far below is fine, the support
     * pillars reach through it — so a maze is never cut open by a lava sea in the Nether or by
     * an aquifer in the Overworld.</p>
     */
    /** The six heights the solid share is counted at, relative to the floor. */
    private static final int[] BURIAL_PROBES =
            {-3, -1, 1, 3, MazePiece.ROOF_DY + 1, MazePiece.ROOF_DY + 3};

    /**
     * How much of what surrounds a floor laid at {@code y} is rock rather than cave.
     *
     * <p>This is the "is it buried, or is it hanging in a cavern" test, and it is unchanged.</p>
     */
    private static float burialSolid(
            java.util.List<net.minecraft.world.gen.chunk.VerticalBlockSample> columns, int y) {
        int solid = 0, total = 0;
        for (var column : columns) {
            for (int dy : BURIAL_PROBES) {
                if (!column.getState(y + dy).isAir()) solid++;
                total++;
            }
        }
        return (float) solid / total;
    }

    /**
     * Is there a fluid where it matters, for a maze under a landmark?
     *
     * <p>The band is the structure's own volume and one block either side of it: its underside
     * slab a block below the floor, its roof, and the skin of rock laid over that roof. Beyond
     * that, there is nothing to protect — the maze is now sealed in non-flammable rock on all six
     * faces, so a pocket of water or lava sitting behind that rock does nothing at all.</p>
     *
     * <p>It was briefly <b>-4 to +9</b>, on the reasoning that a column sample costs a whole
     * NoiseChunk and reading more heights out of one is free. True, and beside the point: the
     * cost of a wider band is not time, it is <b>refusals</b>. Any fluid at any of 350 probes
     * rejected the height outright, and across a hundred and eight blocks of Overworld rock at
     * these depths there is nearly always an aquifer somewhere — so the rule went from strict to
     * unsatisfiable, and every site anybody tried came back "no floor deep enough in rock". A
     * test whose job is to find lava that can reach the maze should look where lava can reach
     * the maze.</p>
     */
    private static int burialFluid(
            java.util.List<net.minecraft.world.gen.chunk.VerticalBlockSample> columns, int y) {
        int wet = 0;
        for (var column : columns) {
            for (int dy = -2; dy <= MazePiece.ROOF_DY + 2; dy++) {
                if (!column.getState(y + dy).getFluidState().isEmpty()) wet++;
            }
        }
        return wet;
    }

    /** How many probes {@link #burialFluid} reads, so a count can be read as a share. */
    private static int burialFluidProbes(int columns) {
        return columns * (MazePiece.ROOF_DY + 5);
    }

    /**
     * How much of the rock around a buried maze may be fluid before the site is refused.
     *
     * <p>A tenth, and the number is bracketed by measurement rather than chosen. Seven sites
     * surveyed in game: the driest height at one of them had <b>8 of 250 probes wet</b> — a
     * puddle in one corner of a hundred-block square, behind a sealed wall of rock, which harms
     * nothing — and at another it had <b>86 of 250</b>, which is a maze sitting inside an
     * aquifer. Any threshold between those two separates them; a tenth has three times the
     * margin on each side.</p>
     *
     * <p>Zero tolerance is what the rule said before, and it was refusing the first of those
     * two as firmly as the second.</p>
     */
    private static final float MAX_WET_SHARE = 0.10f;

    /**
     * The Nether's own fluid test, left exactly as it was.
     *
     * <p>Six spaced probes, gaps and all. It has a lava sea under it and years of play behind it,
     * and the band above is new and unmeasured; changing both at once would leave neither
     * answerable.</p>
     */
    private static boolean netherFluid(
            java.util.List<net.minecraft.world.gen.chunk.VerticalBlockSample> columns, int y) {
        for (var column : columns) {
            for (int dy : BURIAL_PROBES) {
                if (!column.getState(y + dy).getFluidState().isEmpty()) return true;
            }
        }
        return false;
    }

    /**
     * The <b>shallowest</b> floor between {@code minFloor} and {@code maxFloor} that is buried
     * in enough solid rock — not the best-buried one.
     *
     * <p>Which is the opposite of what {@link #findBuriedFloorY} wants, and deliberately. A
     * Nether maze is found by walking into its doorway, so where it sits only has to be solid;
     * a maze under a landmark is reached by a staircase somebody has to walk down, and every
     * block of depth is four more steps of spiral. So this one takes the first acceptable floor
     * on the way down and stops. Deep is a cost here, not a virtue.</p>
     */
    public static OptionalInt findShallowestBuriedFloorY(
            ChunkGenerator gen, net.minecraft.world.HeightLimitView world,
            net.minecraft.world.gen.noise.NoiseConfig noise, int centerX, int centerZ,
            MazeSize size, int minFloor, int maxFloor) {
        if (maxFloor < minFloor) return OptionalInt.empty();
        return surveyBurial(gen, world, noise, centerX, centerZ, size, minFloor, maxFloor).found();
    }

    /**
     * Why a burial search answered what it did: one line per reason, so a refusal can be read
     * instead of guessed at.
     *
     * @param candidates how many floor heights were in range at all
     * @param wet        how many were refused because a fluid was in the way
     * @param open       how many were refused for sitting in too much cave
     * @param bestRatio  the best solid share seen among the heights no fluid refused, or -1
     */
    public record BurialReport(OptionalInt found, int candidates, int wet, int open,
                               float bestRatio, int leastFluid, int fluidProbes) {
        public String why() {
            if (found.isPresent()) return "ok";
            if (candidates == 0) return "no height in range";
            if (wet == candidates) {
                return "every height was too wet, the driest with " + leastFluid
                        + " of " + fluidProbes + " probes in fluid (the limit is "
                        + Math.round(fluidProbes * MAX_WET_SHARE) + ")";
            }
            return "best rock cover " + Math.round(bestRatio * 100) + "% of the "
                    + MIN_SOLID_RATIO_PERCENT + "% needed";
        }
    }

    /** {@link #MIN_SOLID_RATIO} as a percentage, for messages. */
    public static final int MIN_SOLID_RATIO_PERCENT = 60;

    public static BurialReport surveyBurial(
            ChunkGenerator gen, net.minecraft.world.HeightLimitView world,
            net.minecraft.world.gen.noise.NoiseConfig noise, int centerX, int centerZ,
            MazeSize size, int minFloor, int maxFloor) {
        if (maxFloor < minFloor) {
            return new BurialReport(OptionalInt.empty(), 0, 0, 0, -1f, -1, 0);
        }
        var columns = burialColumns(gen, world, noise, centerX, centerZ, size);
        int candidates = 0, wet = 0, open = 0, leastFluid = Integer.MAX_VALUE;
        float bestRatio = -1f;
        for (int y = maxFloor; y >= minFloor; y--) {
            candidates++;
            int fluid = burialFluid(columns, y);
            leastFluid = Math.min(leastFluid, fluid);
            if (fluid > burialFluidProbes(columns.size()) * MAX_WET_SHARE) {
                wet++;
                continue;
            }
            float ratio = burialSolid(columns, y);
            bestRatio = Math.max(bestRatio, ratio);
            if (ratio >= MIN_SOLID_RATIO) {
                return new BurialReport(OptionalInt.of(y), candidates, wet, open, ratio,
                        0, burialFluidProbes(columns.size()));
            }
            open++;
        }
        return new BurialReport(OptionalInt.empty(), candidates, wet, open, bestRatio,
                leastFluid == Integer.MAX_VALUE ? -1 : leastFluid,
                burialFluidProbes(columns.size()));
    }

    /**
     * Enclosed mazes: the entrance goes on the side where the rock just outside the maze is the
     * most open (air at walking height), so the doorway tends to open onto a cave.
     */
    private static int mostOpenSide(Context context, int centerX, int centerZ, MazeSize size, int floorY) {
        ChunkGenerator gen = context.chunkGenerator();
        int dist = size.span() / 2 + 4 + 3; // just past the sealed outer wall (enclosed margin = 4)
        int[][] sideDirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // N, S, W, E
        int best = context.random().nextInt(4);
        int bestAir = -1;
        for (int side = 0; side < 4; side++) {
            int air = 0;
            for (int k = -2; k <= 2; k++) {
                int along = k * size.span() / 6;
                int x = centerX + sideDirs[side][0] * dist + (sideDirs[side][0] == 0 ? along : 0);
                int z = centerZ + sideDirs[side][1] * dist + (sideDirs[side][1] == 0 ? along : 0);
                var column = gen.getColumnSample(x, z, context.world(), context.noiseConfig());
                for (int dy = 1; dy <= 3; dy++) {
                    if (column.getState(floorY + dy).isAir()) air++;
                }
            }
            if (air > bestAir) {
                bestAir = air;
                best = side;
            }
        }
        return best;
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.MAZE;
    }
}
