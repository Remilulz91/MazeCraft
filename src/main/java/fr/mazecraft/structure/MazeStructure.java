package fr.mazecraft.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.mazecraft.MazeCraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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

    /** JSON: the usual structure fields + {@code "style": "hedge" | "desert" | "snow" | "jungle"...}. */
    public static final MapCodec<MazeStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Structure.configCodecBuilder(instance),
            Codec.STRING.optionalFieldOf("style", "hedge").forGetter(s -> s.style.id())
    ).apply(instance, MazeStructure::new));

    private final MazeStyle style;

    /** Floor height range of enclosed (Nether) mazes. Lava sea is at Y=31, bedrock roof from Y=123. */
    private static final int NETHER_MIN_FLOOR = 40;
    private static final int NETHER_MAX_FLOOR = 80;

    /** Rejects the spot if the terrain height varies more than this across the footprint. */
    private static final int MAX_HEIGHT_DIFFERENCE = 12;

    public MazeStructure(Structure.Config config, String styleId) {
        super(config);
        MazeStyle parsed = MazeStyle.fromId(styleId);
        if (parsed == null) {
            MazeCraft.LOGGER.warn("[MazeCraft] Unknown maze style '{}' in worldgen JSON, using hedge", styleId);
            parsed = MazeStyle.HEDGE;
        }
        this.style = parsed;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunkPos = context.chunkPos();
        MazeSize rolled = style.isEnd() ? MazeSize.rollEnd(context.random()) : MazeSize.roll(context.random());
        long mazeSeed = context.random().nextLong();
        int centerX = chunkPos.getCenterX();
        int centerZ = chunkPos.getCenterZ();

        if (style.enclosed) {
            // Nether: buried in the rock like a fortress, at the height where the rock is the most
            // solid (never hanging over the lava sea); smaller sizes are tried if nothing fits.
            // No colossal mazes in the Nether: too big for its caverns
            MazeSize netherSize = rolled == MazeSize.COLOSSAL ? MazeSize.LARGE : rolled;
            for (MazeSize size = netherSize; size != null; size = size.smaller()) {
                if (StructureAvoidance.isNearOtherStructure(context, size, style)) continue;
                OptionalInt floorY = findBuriedFloorY(context, centerX, centerZ, size);
                if (floorY.isPresent()) {
                    final MazeSize finalSize = size;
                    final int y = floorY.getAsInt();
                    final int entrance = mostOpenSide(context, centerX, centerZ, finalSize, y);
                    return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector ->
                            collector.addPiece(new MazePiece(style, finalSize, mazeSeed, centerX, y, centerZ, entrance))));
                }
            }
            return Optional.empty();
        }

        // Big mazes need a big flat area: if the rolled size doesn't fit, try the smaller ones.
        for (MazeSize size = rolled; size != null; size = size.smaller()) {
            if (StructureAvoidance.isNearOtherStructure(context, size, style)) continue;
            OptionalInt floorY = findFloorY(context, centerX, centerZ, size);
            if (floorY.isPresent()) {
                final MazeSize finalSize = size;
                final int y = floorY.getAsInt();
                final int entrance = bestEntranceSide(context, centerX, centerZ, finalSize, y, style);
                return Optional.of(new StructurePosition(new BlockPos(centerX, y, centerZ), collector ->
                        collector.addPiece(new MazePiece(style, finalSize, mazeSeed, centerX, y, centerZ, entrance))));
            }
        }
        return Optional.empty();
    }

    /**
     * Samples the terrain on a 5×5 grid over the footprint. Returns the floor Y, or empty
     * if there is water or more than {@link #MAX_HEIGHT_DIFFERENCE} blocks of relief.
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
        if (max - min > MAX_HEIGHT_DIFFERENCE) {
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
    private static OptionalInt findBuriedFloorY(Context context, int centerX, int centerZ, MazeSize size) {
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
        for (int y = NETHER_MIN_FLOOR + offset; y <= NETHER_MAX_FLOOR; y += 2) {
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
