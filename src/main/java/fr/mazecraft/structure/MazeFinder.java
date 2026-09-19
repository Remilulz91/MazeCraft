package fr.mazecraft.structure;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the naturally generated maze at a position (same lookup vanilla uses for
 * "is this player inside a structure": structure references of the chunk → starts).
 *
 * Mazes built with {@code /maze debug place} are not real structures and are not found.
 */
public final class MazeFinder {

    private MazeFinder() { }

    /** A maze found at a position: its structure start (unique id = start chunk) and its piece. */
    public record MazeHit(StructureStart start, MazePiece piece) {
        /** Unique key of this maze in its dimension. */
        public long key() {
            return start.getPos().toLong();
        }
    }

    /**
     * @param extraAbove how many blocks above the piece bounding box still count as "in the maze"
     *                   (used to catch players walking on / flying over the walls)
     */
    public static MazeHit find(ServerWorld world, BlockPos pos, int extraAbove) {
        return find(world, pos, extraAbove, false);
    }

    /** Like {@link #find(ServerWorld, BlockPos, int)} but only checks X/Z (any height). */
    public static MazeHit findInColumn(ServerWorld world, BlockPos pos) {
        return find(world, pos, 0, true);
    }

    private static MazeHit find(ServerWorld world, BlockPos pos, int extraAbove, boolean anyY) {
        StructureAccessor accessor = world.getStructureAccessor();
        Map<Structure, LongSet> references = accessor.getStructureReferences(pos);
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            Structure structure = entry.getKey();
            if (structure.getType() != ModStructures.MAZE) continue;

            LongIterator it = entry.getValue().iterator();
            while (it.hasNext()) {
                ChunkPos startChunk = new ChunkPos(it.nextLong());
                StructureStart start = accessor.getStructureStart(
                        ChunkSectionPos.from(startChunk, 0), structure,
                        world.getChunk(startChunk.x, startChunk.z, ChunkStatus.STRUCTURE_STARTS));
                if (start == null || !start.hasChildren()) continue;

                for (StructurePiece piece : start.getChildren()) {
                    if (piece instanceof MazePiece maze && contains(maze.getBoundingBox(), pos, extraAbove, anyY)) {
                        return new MazeHit(start, maze);
                    }
                }
            }
        }
        return null;
    }

    /** The box covers the maze above its floor; floor + foundation below it count too. */
    private static final int BELOW = MazePiece.FOUNDATION_DEPTH + 1;

    private static boolean contains(BlockBox box, BlockPos pos, int extraAbove, boolean anyY) {
        return pos.getX() >= box.getMinX() && pos.getX() <= box.getMaxX()
                && pos.getZ() >= box.getMinZ() && pos.getZ() <= box.getMaxZ()
                && (anyY || (pos.getY() >= box.getMinY() - BELOW && pos.getY() <= box.getMaxY() + extraAbove));
    }

    /** Every natural maze whose box overlaps this (loaded) chunk. */
    public static List<MazeHit> findInChunk(ServerWorld world, ChunkPos chunkPos) {
        List<MazeHit> hits = new ArrayList<>();
        StructureAccessor accessor = world.getStructureAccessor();
        Map<Structure, LongSet> references = world.getChunk(chunkPos.x, chunkPos.z).getStructureReferences();
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            Structure structure = entry.getKey();
            if (structure.getType() != ModStructures.MAZE) continue;
            LongIterator it = entry.getValue().iterator();
            while (it.hasNext()) {
                ChunkPos startChunk = new ChunkPos(it.nextLong());
                StructureStart start = accessor.getStructureStart(
                        ChunkSectionPos.from(startChunk, 0), structure,
                        world.getChunk(startChunk.x, startChunk.z, ChunkStatus.STRUCTURE_STARTS));
                if (start == null || !start.hasChildren()) continue;
                for (StructurePiece piece : start.getChildren()) {
                    if (piece instanceof MazePiece maze && maze.getBoundingBox().intersectsXZ(
                            chunkPos.getStartX(), chunkPos.getStartZ(), chunkPos.getEndX(), chunkPos.getEndZ())) {
                        hits.add(new MazeHit(start, maze));
                    }
                }
            }
        }
        return hits;
    }
}
