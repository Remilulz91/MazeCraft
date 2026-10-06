package fr.mazecraft.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.mazecraft.MazeCraft;
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

        if (style.enclosed) {
            // Nether: buried in the rock like a fortress, at the height where the rock is the
            // most solid (never hanging over the lava sea). No colossal mazes down here.
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
     * Samples the terrain on a 5×5 grid over the footprint. Returns the floor Y, or empty
     * if there is water or more relief than {@link MazeSize#maxRelief()} allows.
     */
    private static OptionalInt findFloorY(Context context, int centerX, int centerZ, MazeSize size) {
        ChunkGenerator gen = context.chunkGenerator();
        int half = size.span() / 2;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, n = 0;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = centerX + i * half / 2;
                int z = centerZ + j * half / 2;
                int surface = gen.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                int ground = gen.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
                if (surface != ground) {
                    return OptionalInt.empty(); // water (lake, river, ocean) in the footprint
                }
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                sum += surface;
                n++;
            }
        }
        if (max - min > size.maxRelief()) {
            return OptionalInt.empty(); // too hilly
        }
        // getHeight returns the first air block → the floor replaces the surface block below it
        return OptionalInt.of(Math.round((float) sum / n) - 1);
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
        ChunkGenerator gen = context.chunkGenerator();
        int half = size.span() / 2 + 4;
        var columns = new java.util.ArrayList<net.minecraft.world.gen.chunk.VerticalBlockSample>();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                columns.add(gen.getColumnSample(centerX + i * half / 2, centerZ + j * half / 2,
                        context.world(), context.noiseConfig()));
            }
        }
        // Relative to the floor. No lava allowed at maze level; lava far below is fine (pillars).
        int[] probes = {-3, -1, 1, 3, MazePiece.ROOF_DY + 1, MazePiece.ROOF_DY + 3};
        int bestY = -1;
        float bestRatio = -1f;
        int offset = context.random().nextInt(2); // vary the parity between mazes
        for (int y = minFloor + offset; y <= maxFloor; y += 2) {
            int solid = 0, total = 0;
            boolean lava = false;
            for (var column : columns) {
                for (int dy : probes) {
                    var state = column.getState(y + dy);
                    if (!state.getFluidState().isEmpty()) lava = true;
                    if (!state.isAir() && state.getFluidState().isEmpty()) solid++;
                    total++;
                }
            }
            if (lava) continue;
            float ratio = (float) solid / total;
            if (ratio > bestRatio) {
                bestRatio = ratio;
                bestY = y;
            }
        }
        return bestRatio >= MIN_SOLID_RATIO ? OptionalInt.of(bestY) : OptionalInt.empty();
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
