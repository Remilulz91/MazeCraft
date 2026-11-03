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
        MazeHit loose = null;
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
                    if (!(piece instanceof MazePiece maze)) continue;
                    // The floor this position is actually in wins outright. Only if no floor
                    // holds it does the tolerance above the walls get a say, and then the
                    // highest floor takes it -- someone over a stack is over its roof.
                    if (contains(maze, pos, 0, anyY)) return new MazeHit(start, maze);
                    if (extraAbove > 0 && contains(maze, pos, extraAbove, anyY)
                            && (loose == null || maze.getFloorY() > loose.piece().getFloorY())) {
                        loose = new MazeHit(start, maze);
                    }
                }
            }
        }
        return loose;
    }

    /** The box covers the maze above its floor; floor + foundation below it count too. */
    private static final int BELOW = MazePiece.FOUNDATION_DEPTH + 1;

    /**
     * How far below its box a floor still owns, which is not the same for every floor.
     *
     * <p>The bottom one owns its whole foundation — thirty-two blocks of it, and the vault and
     * the crypt that hang under it. A floor with another floor underneath owns one block below
     * its box, which is the slab it stands on, and nothing more. Giving every floor the
     * foundation's reach made the top piece of a stack answer for the two floors below it, since
     * they sit six and twelve blocks down and the pieces are asked in order from the top: a lever
     * on the second floor was looked up in the first floor's layout and found nowhere, so it did
     * nothing at all.</p>
     */
    private static int below(MazePiece maze) {
        return maze.isBottomFloor() ? BELOW : 1;
    }

    private static boolean contains(MazePiece maze, BlockPos pos, int extraAbove, boolean anyY) {
        BlockBox box = maze.getBoundingBox();
        return pos.getX() >= box.getMinX() && pos.getX() <= box.getMaxX()
                && pos.getZ() >= box.getMinZ() && pos.getZ() <= box.getMaxZ()
                && (anyY || (pos.getY() >= box.getMinY() - below(maze)
                        && pos.getY() <= box.getMaxY() + extraAbove));
    }

    /**
     * Every floor of this maze, deepest last.
     *
     * <p>A maze is one structure and its floors are its pieces, so anything that belongs to the
     * maze rather than to the floor the player happens to stand on — the vault, the crypt, the
     * digits of a code spread down the stack — has to be asked of all of them.</p>
     */
    public static List<MazePiece> floorsOf(MazeHit hit) {
        List<MazePiece> floors = new ArrayList<>();
        for (StructurePiece piece : hit.start().getChildren()) {
            if (piece instanceof MazePiece maze) floors.add(maze);
        }
        floors.sort((a, b) -> Integer.compare(b.getFloorY(), a.getFloorY()));
        return floors;
    }

    /** The deepest floor of this maze: where the crypt and the vault are. */
    public static MazeHit bottomFloor(MazeHit hit) {
        MazePiece deepest = hit.piece();
        for (MazePiece floor : floorsOf(hit)) {
            if (floor.getFloorY() < deepest.getFloorY()) deepest = floor;
        }
        return deepest == hit.piece() ? hit : new MazeHit(hit.start(), deepest);
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
