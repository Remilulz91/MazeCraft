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
        MazeSize rolled = MazeSize.roll(context.random());
        long mazeSeed = context.random().nextLong();
        int centerX = chunkPos.getCenterX();
        int centerZ = chunkPos.getCenterZ();

        // Big mazes need a big flat area: if the rolled size doesn't fit, try the smaller ones.
        for (MazeSize size = rolled; size != null; size = size.smaller()) {
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

    @Override
    public StructureType<?> getType() {
        return ModStructures.MAZE;
    }
}
