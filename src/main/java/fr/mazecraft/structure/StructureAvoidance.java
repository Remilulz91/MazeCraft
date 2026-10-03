package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.Set;

/**
 * Keeps mazes away from other structures (villages, temples, end cities, fortresses,
 * bastions... including modded ones), the same way vanilla keeps pillager outposts away
 * from villages — but against every structure set, not just one.
 *
 * For each other structure set with a random-spread placement, the possible start chunks
 * around the maze are computed from the world seed; a start counts only if it passes the
 * set's frequency and its structure accepts the biome there. If one is too close, the maze
 * doesn't generate at this size.
 */
public final class StructureAvoidance {

    /** Assumed radius of another structure around its start chunk, when it is not in {@link #RADIUS}. */
    private static final int DEFAULT_RADIUS_CHUNKS = 5;

    /**
     * How far each vanilla structure set actually sprawls from its start chunk, in chunks.
     *
     * <p>A flat 5 was both too small and too large: a big plains village or a nether fortress
     * reaches well past 5 chunks (which is how a village ended up sliced in half by a large
     * maze), while a single igloo or desert pyramid fits in one — and blocking 5 chunks around
     * every one of those rejected far more maze spots than it needed to.</p>
     */
    private static final Map<Identifier, Integer> RADIUS = Map.ofEntries(
            // Sprawling: these grow far beyond their start chunk
            Map.entry(Identifier.ofVanilla("nether_complexes"), 9),   // fortresses crawl a long way
            Map.entry(Identifier.ofVanilla("villages"), 8),
            Map.entry(Identifier.ofVanilla("end_cities"), 8),
            Map.entry(Identifier.ofVanilla("woodland_mansions"), 7),
            Map.entry(Identifier.ofVanilla("ocean_monuments"), 6),
            // Compact: one building, no reason to sterilise 5 chunks around it
            Map.entry(Identifier.ofVanilla("trail_ruins"), 3),
            Map.entry(Identifier.ofVanilla("pillager_outposts"), 2),
            Map.entry(Identifier.ofVanilla("desert_pyramids"), 2),
            Map.entry(Identifier.ofVanilla("jungle_temples"), 2),
            Map.entry(Identifier.ofVanilla("swamp_huts"), 2),
            Map.entry(Identifier.ofVanilla("igloos"), 2),
            Map.entry(Identifier.ofVanilla("ruined_portals"), 1),
            Map.entry(Identifier.ofVanilla("shipwrecks"), 1)
    );

    /**
     * Sets NOT avoided: underground or tiny structures that a surface maze doesn't really
     * cut, and very frequent ones that would prevent most mazes from generating.
     */
    private static final Set<Identifier> IGNORED = Set.of(
            Identifier.ofVanilla("mineshafts"),
            Identifier.ofVanilla("strongholds"),
            Identifier.ofVanilla("buried_treasures"),
            Identifier.ofVanilla("ancient_cities"),
            Identifier.ofVanilla("trial_chambers"),
            Identifier.ofVanilla("nether_fossils"),
            Identifier.ofVanilla("ocean_ruins")
    );

    /** Sets a DEEP structure may ignore: they only ever appear at or near the surface. */
    private static final Set<Identifier> SURFACE_ONLY = Set.of(
            Identifier.ofVanilla("villages"),
            Identifier.ofVanilla("pillager_outposts"),
            Identifier.ofVanilla("desert_pyramids"),
            Identifier.ofVanilla("jungle_temples"),
            Identifier.ofVanilla("swamp_huts"),
            Identifier.ofVanilla("igloos"),
            Identifier.ofVanilla("woodland_mansions"),
            Identifier.ofVanilla("ocean_monuments"),
            Identifier.ofVanilla("ocean_ruins"),
            Identifier.ofVanilla("shipwrecks"),
            Identifier.ofVanilla("buried_treasures"),
            Identifier.ofVanilla("trail_ruins"),
            Identifier.ofVanilla("ruined_portals")
    );

    private StructureAvoidance() { }

    /** True if another structure could start close enough to overlap a maze of this size here. */
    public static boolean isNearOtherStructure(Structure.Context context, MazeSize size, MazeStyle style) {
        ChunkPos center = context.chunkPos();
        int mazeRadius = (size.span() / 2 + style.margin) / 16 + 1;
        long seed = context.seed();

        int ownRank = rank(style, size);
        // Kronos is the one maze that lives underground, so the structures a surface maze can
        // safely ignore are exactly the ones it must not: ancient cities sit at its depth.
        boolean underground = style.isKronos();

        Registry<StructureSet> sets = context.dynamicRegistryManager().get(RegistryKeys.STRUCTURE_SET);
        for (Map.Entry<RegistryKey<StructureSet>, StructureSet> entry : sets.getEntrySet()) {
            Identifier id = entry.getKey().getValue();
            if (!underground && IGNORED.contains(id)) continue;
            if (underground && SURFACE_ONLY.contains(id)) continue;
            StructureSet set = entry.getValue();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;

            int radius;
            if (id.getNamespace().equals(MazeCraft.MOD_ID)) {
                // Since 0.8.0 every style and size has its own spread grid, so two DIFFERENT
                // mazes can land on each other — nothing used to stop them, because we skipped
                // our own namespace wholesale. They now avoid each other, but only one of the
                // two may back off: if both did, neither would generate. The bigger maze wins,
                // ties broken by style order, so the decision is the same whichever side asks.
                int otherRank = rankOf(set);
                if (otherRank <= ownRank) continue;
                radius = mazeRadius + otherMazeRadius(set);
            } else {
                radius = mazeRadius + RADIUS.getOrDefault(id, DEFAULT_RADIUS_CHUNKS);
            }

            int spacing = placement.getSpacing();
            int rx0 = Math.floorDiv(center.x - radius, spacing), rx1 = Math.floorDiv(center.x + radius, spacing);
            int rz0 = Math.floorDiv(center.z - radius, spacing), rz1 = Math.floorDiv(center.z + radius, spacing);
            for (int rx = rx0; rx <= rx1; rx++) {
                for (int rz = rz0; rz <= rz1; rz++) {
                    ChunkPos start = placement.getStartChunk(seed, rx * spacing, rz * spacing);
                    if (Math.abs(start.x - center.x) > radius || Math.abs(start.z - center.z) > radius) continue;
                    if (start.equals(center)) continue; // ourselves
                    if (!placement.applyFrequencyReduction(start.x, start.z, seed)) continue;
                    if (anyStructureFitsBiome(context, set, start)) return true;
                }
            }
        }
        return false;
    }

    /**
     * True if any structure of this set accepts the biome at that start chunk.
     *
     * <p>The biome is sampled at <b>two</b> heights: sea level and the surface. Vanilla checks a
     * structure's biome at its own placement height, which for a surface structure is the
     * terrain — and on a plateau or in mountains the biome at sea level is a different one. A
     * single sea-level sample therefore said "no village here" for villages that did generate.
     * Two samples cost one extra height query and can only make the maze more cautious.</p>
     */
    /**
     * Priority of a maze, so that of two overlapping candidates exactly one steps aside:
     * the bigger footprint wins, and equal sizes are ordered by style.
     */
    private static int rank(MazeStyle style, MazeSize size) {
        return size.step().ordinal() * 100 + style.ordinal();
    }

    /** Priority of the maze a mazecraft structure set places, or -1 if it places none. */
    private static int rankOf(StructureSet set) {
        int best = -1;
        for (StructureSet.WeightedEntry weighted : set.structures()) {
            if (weighted.structure().value() instanceof MazeStructure maze) {
                best = Math.max(best, rank(maze.getStyle(), maze.getSize()));
            }
        }
        return best;
    }

    /** Half-footprint, in chunks, of the maze a mazecraft structure set places. */
    private static int otherMazeRadius(StructureSet set) {
        int best = DEFAULT_RADIUS_CHUNKS;
        for (StructureSet.WeightedEntry weighted : set.structures()) {
            if (weighted.structure().value() instanceof MazeStructure maze) {
                best = Math.max(best, (maze.getSize().span() / 2 + maze.getStyle().margin) / 16 + 1);
            }
        }
        return best;
    }

    private static boolean anyStructureFitsBiome(Structure.Context context, StructureSet set, ChunkPos start) {
        int x = start.getCenterX();
        int z = start.getCenterZ();
        int surface = context.chunkGenerator().getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG,
                context.world(), context.noiseConfig());
        int[] heights = { context.chunkGenerator().getSeaLevel(), surface };
        for (int y : heights) {
            RegistryEntry<Biome> biome = context.biomeSource().getBiome(
                    BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
                    context.noiseConfig().getMultiNoiseSampler());
            for (StructureSet.WeightedEntry weighted : set.structures()) {
                if (weighted.structure().value().getValidBiomes().contains(biome)) return true;
            }
        }
        return false;
    }
}
