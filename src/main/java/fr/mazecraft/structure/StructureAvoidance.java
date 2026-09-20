package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
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

    /** Assumed radius of another structure around its start chunk (villages / end cities ≈ 5 chunks). */
    private static final int OTHER_RADIUS_CHUNKS = 5;

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
            Identifier.ofVanilla("ruined_portals"),
            Identifier.ofVanilla("nether_fossils"),
            Identifier.ofVanilla("shipwrecks"),
            Identifier.ofVanilla("ocean_ruins")
    );

    private StructureAvoidance() { }

    /** True if another structure could start close enough to overlap a maze of this size here. */
    public static boolean isNearOtherStructure(Structure.Context context, MazeSize size, MazeStyle style) {
        ChunkPos center = context.chunkPos();
        int mazeRadius = (size.span() / 2 + style.margin) / 16 + 1;
        int radius = mazeRadius + OTHER_RADIUS_CHUNKS;
        long seed = context.seed();

        Registry<StructureSet> sets = context.dynamicRegistryManager().get(RegistryKeys.STRUCTURE_SET);
        for (Map.Entry<RegistryKey<StructureSet>, StructureSet> entry : sets.getEntrySet()) {
            Identifier id = entry.getKey().getValue();
            if (id.getNamespace().equals(MazeCraft.MOD_ID) || IGNORED.contains(id)) continue;
            StructureSet set = entry.getValue();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;

            int spacing = placement.getSpacing();
            int rx0 = Math.floorDiv(center.x - radius, spacing), rx1 = Math.floorDiv(center.x + radius, spacing);
            int rz0 = Math.floorDiv(center.z - radius, spacing), rz1 = Math.floorDiv(center.z + radius, spacing);
            for (int rx = rx0; rx <= rx1; rx++) {
                for (int rz = rz0; rz <= rz1; rz++) {
                    ChunkPos start = placement.getStartChunk(seed, rx * spacing, rz * spacing);
                    if (Math.abs(start.x - center.x) > radius || Math.abs(start.z - center.z) > radius) continue;
                    if (!placement.applyFrequencyReduction(start.x, start.z, seed)) continue;
                    if (anyStructureFitsBiome(context, set, start)) return true;
                }
            }
        }
        return false;
    }

    private static boolean anyStructureFitsBiome(Structure.Context context, StructureSet set, ChunkPos start) {
        int y = context.chunkGenerator().getSeaLevel();
        RegistryEntry<Biome> biome = context.biomeSource().getBiome(
                BiomeCoords.fromBlock(start.getCenterX()), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(start.getCenterZ()),
                context.noiseConfig().getMultiNoiseSampler());
        for (StructureSet.WeightedEntry weighted : set.structures()) {
            if (weighted.structure().value().getValidBiomes().contains(biome)) return true;
        }
        return false;
    }
}
