package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeTemplates;
import fr.mazecraft.structure.SlabSpiral;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

/**
 * Carrying a building's staircase down from its ground floor to the maze buried under it.
 *
 * <p>Was {@code PagodaStair}, and was written for one building. Nothing in it ever was specific
 * to that building except four numbers and a table of steps, and the table turned out not to be
 * specific either — see {@link SlabSpiral}. What is left is the part that is the same for every
 * landmark: hollow a 3 × 3, line it, stand a pole in the middle, and hang two slabs off the pole
 * per block of height, all the way down to the maze's floor.</p>
 *
 * <p>Everything here is in <b>template coordinates</b> and goes through
 * {@link MazeTemplates.Placement#world}, which is {@code StructureTemplate.transform} — the same
 * static method that lays every block of the template itself. So the shaft stays under the pole
 * whichever way the building is turned by construction, not by two rotation tables agreeing.</p>
 */
public final class LandmarkStair {

    /** The stairwell is the 3 × 3 around the pole; the shaft is that plus a lining one thick. */
    private static final int WELL = 1;

    /** How deep a single call will dig, as a guard against an absurd argument. */
    public static final int MAX_DEPTH = 320;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private LandmarkStair() { }

    /** Where the pole stands at level {@code ty}, in the world. */
    public static BlockPos pole(MazeTemplates.Placement placement, Descent descent, int ty) {
        return placement.world(descent.x(), ty, descent.z());
    }

    /** Horizontal half-width of the shaft, lining included, in blocks. */
    public static int shaftHalf() {
        return WELL + 1;
    }

    /**
     * Carries the staircase down from the building's ground floor to level {@code bottomY} of
     * the template's own vertical scale, where it stops.
     *
     * <p>{@code bottomY} is the last level that gets steps, so the lowest slab laid is a bottom
     * slab there, standing half a block above {@code bottomY}. Arriving on a floor laid at
     * {@code bottomY - 1} is then a half-block step down, which Minecraft walks without a
     * stumble — rather than ending the staircase flush, which would mean replacing one block of
     * that floor with a slab.</p>
     *
     * <p>The ground floor level itself is rewritten, not skipped. The pagoda's template has
     * carpet in six of the nine cells of the stairwell there, resting on the floor this is about
     * to open: left alone it would be floating, and carpet does not float, it drops as an item
     * the first time anything updates it. Hollowing that level and laying its two steps again
     * turns the middle of the ground floor into the top of a stairwell, which is what a
     * staircase going down looks like from above.</p>
     *
     * @param openLevels how many levels at the bottom are left unlined, so the staircase opens
     *                   into the room it arrives in instead of ending in a sealed box
     * @return the number of levels built
     */
    public static int digDown(StructureWorldAccess world, MazeTemplates.Placement placement,
                              Descent descent, int bottomY, int openLevels, BlockBox limit) {
        int levels = 0;
        BlockPos.Mutable at = new BlockPos.Mutable();
        int groundY = descent.groundY();
        // Bottom upwards, which matters for one reason: a block is cleared before the block
        // above it is. Going the other way, the shaft punched through the maze's ceiling first
        // and the lantern hanging from the underside of that very block lost its support and
        // popped off as an item on the plaza floor. Clearing a cell can always dislodge whatever
        // hangs beneath it, so the order is the fix rather than a list of blocks to look out for.
        //
        // The depth guard clamps the bottom rather than the loop, so a shaft that would be too
        // long is cut short underground instead of stopping before it reaches the building.
        int from = Math.max(bottomY, groundY - MAX_DEPTH + 1);
        for (int ty = from; ty <= groundY; ty++, levels++) {
            // 1. The stairwell: 3 x 3 of air around the pole, so there is something to walk down.
            for (int dx = -WELL; dx <= WELL; dx++) {
                for (int dz = -WELL; dz <= WELL; dz++) {
                    set(world, at, placement, descent.x() + dx, ty, descent.z() + dz, AIR, limit);
                }
            }
            // 2. The lining: one block of masonry all round, so the shaft meets a wall rather
            //    than whatever cave, aquifer or seam of gravel it happens to pass through.
            //
            //    Not at the two top levels: those are the building's own ground floor and the
            //    course under it, which are part of the building and stay as they were built.
            //
            //    And not at the bottom {@code openLevels}, which was the mistake that made the
            //    first one unwalkable. The lining ran all the way down, so the stairs ended
            //    inside a sealed box standing in the middle of the room they were supposed to
            //    arrive in — the one place the shaft must have no walls at all is where it comes
            //    out. The level above those is still lined: that is the maze's ceiling, and the
            //    ring there is the rim of the hole in it.
            if (ty <= groundY - 2 && ty >= from + openLevels) {
                for (int dx = -WELL - 1; dx <= WELL + 1; dx++) {
                    for (int dz = -WELL - 1; dz <= WELL + 1; dz++) {
                        if (Math.abs(dx) <= WELL && Math.abs(dz) <= WELL) continue;
                        set(world, at, placement, descent.x() + dx, ty, descent.z() + dz,
                                descent.lining(), limit);
                    }
                }
            }
            // 3. The pole, and the two steps of this level.
            set(world, at, placement, descent.x(), ty, descent.z(), descent.pole(), limit);
            for (int half = 0; half < SlabSpiral.HALVES; half++) {
                int[] cell = descent.spiral().cell(ty, half);
                set(world, at, placement, descent.x() + cell[0], ty, descent.z() + cell[1],
                        half == 0 ? descent.stepBottom() : descent.stepTop(), limit);
            }
        }
        return levels;
    }

    private static void set(StructureWorldAccess world, BlockPos.Mutable scratch,
                            MazeTemplates.Placement placement, int tx, int ty, int tz,
                            BlockState state, BlockBox limit) {
        BlockPos pos = placement.world(tx, ty, tz);
        scratch.set(pos);
        // Generation runs one chunk at a time, and a write outside the chunk being generated
        // reaches for a chunk that does not exist yet. Null is for a command on a loaded world.
        if (limit != null && !limit.contains(scratch)) return;
        world.setBlockState(scratch, state, Block.NOTIFY_LISTENERS);
    }
}
