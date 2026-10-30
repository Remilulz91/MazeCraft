package fr.mazecraft.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

/**
 * The spiral staircase inside the cherry pagoda, and how to carry on down it.
 *
 * <p>The pagoda was built by hand and the staircase in the middle of it is part of the building,
 * not something this mod invented: a pole with two slabs winding round it, one quarter turn per
 * block of height, twenty-nine levels of it from the ground floor to the top room. The maze is
 * going underneath, so the way down has to be the same staircase continued, not a ladder or a
 * hole in the floor beside it.</p>
 *
 * <p>Which means the pattern had to be read out of the template rather than guessed, and it took
 * three attempts to even find it. The first look searched for {@code *_stairs} blocks and found
 * only the decorative eaves; the second looked for straight runs of them. Neither could see it,
 * because it is not made of stairs at all — it is <b>slabs</b>, two per level, a bottom one and a
 * top one, so each level is climbed in two half steps. The pattern below is a transcription of
 * what is in the file:</p>
 *
 * <pre>
 *   pole                  andesite_wall at (12,12), every level from y=3 to y=31
 *   y mod 4 == 3          bottom (11,11)   top (11,12)
 *   y mod 4 == 0          bottom (11,13)   top (12,13)
 *   y mod 4 == 1          bottom (13,13)   top (13,12)
 *   y mod 4 == 2          bottom (13,11)   top (12,11)
 * </pre>
 *
 * <p>Twenty-nine levels, no exception, so the period is not an observation about the part that
 * was measured — it is the rule the whole staircase obeys. And because it is keyed on
 * {@code y mod 4}, continuing it downwards needs no special case at all: {@code floorMod} of a
 * negative level gives the phase that carries straight on from the level above it. The step
 * below the ground floor's first step is the one the formula already names.</p>
 *
 * <p>Everything here is in <b>template coordinates</b> and goes through
 * {@link MazeTemplates.Placement#world}, which is {@code StructureTemplate.transform} — the same
 * static method that lays every block of the template itself. So the shaft stays under the pole
 * whichever way the pagoda is turned by construction, not by two rotation tables agreeing.</p>
 */
public final class PagodaStair {

    /** The pole the steps wind around, in template coordinates. */
    public static final int POLE_X = 12;
    public static final int POLE_Z = 12;

    /** The ground floor: the lowest level of the staircase as it was built. */
    public static final int GROUND_Y = 3;

    /** Levels per full turn. */
    private static final int PERIOD = 4;

    /** The stairwell is the 3x3 around the pole; the shaft is that plus a lining one block thick. */
    private static final int WELL = 1;

    /** Bottom-slab cell per phase, phases 0..3 — transcribed from the template. */
    private static final int[][] BOTTOM = {{11, 13}, {13, 13}, {13, 11}, {11, 11}};
    /** Top-slab cell per phase, phases 0..3. */
    private static final int[][] TOP = {{12, 13}, {13, 12}, {12, 11}, {11, 12}};

    /** How deep a single call will dig, as a guard against an absurd argument. */
    public static final int MAX_DEPTH = 320;

    private PagodaStair() { }

    /**
     * Where the pole stands at level {@code ty}, in the world.
     *
     * <p>Worth a method of its own because the pole is <b>not</b> the middle of the footprint:
     * the pagoda is 26 blocks across and the staircase sits at 12, half a block west of the 13
     * the piece is centred on. So the foot of the stairs moves one block as the building turns —
     * 101, 100, 99, 100 over the four facings, in the check that measured it. Anything that has
     * to meet the stairs down there, the maze included, has to ask this rather than assume the
     * centre of the pagoda.</p>
     */
    public static BlockPos pole(MazeTemplates.Placement placement, int ty) {
        return placement.world(POLE_X, ty, POLE_Z);
    }

    private static final BlockState POLE = Blocks.ANDESITE_WALL.getDefaultState();
    private static final BlockState STEP_BOTTOM = Blocks.POLISHED_ANDESITE_SLAB.getDefaultState()
            .with(SlabBlock.TYPE, SlabType.BOTTOM);
    private static final BlockState STEP_TOP = Blocks.POLISHED_ANDESITE_SLAB.getDefaultState()
            .with(SlabBlock.TYPE, SlabType.TOP);
    private static final BlockState LINING = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    /**
     * Carries the staircase down from the pagoda's ground floor to level {@code bottomY} of the
     * template's own vertical scale, where it stops.
     *
     * <p>{@code bottomY} is the last level that gets steps, so the lowest slab laid is a bottom
     * slab there, standing half a block above {@code bottomY}. Arriving on a floor laid at
     * {@code bottomY - 1} is then a half-block step down, which Minecraft walks without a
     * stumble — rather than ending the staircase flush, which would mean replacing one block of
     * that floor with a slab.</p>
     *
     * <p>The ground floor level itself is rewritten, not skipped. The template has carpet in six
     * of the nine cells of the stairwell there, resting on the floor this is about to open: left
     * alone it would be floating, and carpet does not float, it drops as an item the first time
     * anything updates it. Hollowing that level and laying its two steps again turns the middle
     * of the pagoda's ground floor into the top of a stairwell, which is what a staircase going
     * down looks like from above.</p>
     *
     * @param openLevels how many levels at the bottom are left unlined, so the staircase opens
     *                   into the room it arrives in instead of ending in a sealed box
     * @return the number of levels built
     */
    public static int digDown(StructureWorldAccess world, MazeTemplates.Placement placement,
                              int bottomY, int openLevels, BlockBox limit) {
        int levels = 0;
        BlockPos.Mutable at = new BlockPos.Mutable();
        // Bottom upwards, which matters for one reason: a block is cleared before the block
        // above it is. Going the other way, the shaft punched through the maze's ceiling first
        // and the lantern hanging from the underside of that very block lost its support and
        // popped off as an item on the plaza floor. Clearing a cell can always dislodge whatever
        // hangs beneath it, so the order is the fix rather than a list of blocks to look out for.
        //
        // The depth guard clamps the bottom rather than the loop, so a shaft that would be too
        // long is cut short underground instead of stopping before it reaches the pagoda.
        int from = Math.max(bottomY, GROUND_Y - MAX_DEPTH + 1);
        for (int ty = from; ty <= GROUND_Y; ty++, levels++) {
            // 1. The stairwell: 3x3 of air around the pole, so there is something to walk down.
            for (int dx = -WELL; dx <= WELL; dx++) {
                for (int dz = -WELL; dz <= WELL; dz++) {
                    set(world, at, placement, POLE_X + dx, ty, POLE_Z + dz, AIR, limit);
                }
            }
            // 2. The lining: one block of masonry all round, so the shaft meets a wall rather
            //    than whatever cave, aquifer or seam of gravel it happens to pass through.
            //
            //    Not at the two top levels: those are the pagoda's own ground floor and the
            //    course under it, which are part of the building and stay as they were built.
            //
            //    And not at the bottom {@code openLevels}, which was the mistake that made the
            //    first one unwalkable. The lining ran all the way down, so the stairs ended
            //    inside a sealed andesite box standing in the middle of the room they were
            //    supposed to arrive in — the one place the shaft must have no walls at all is
            //    where it comes out. The level above those is still lined: that is the maze's
            //    ceiling, and the ring of andesite there is the rim of the hole in it.
            if (ty <= GROUND_Y - 2 && ty >= from + openLevels) {
                for (int dx = -WELL - 1; dx <= WELL + 1; dx++) {
                    for (int dz = -WELL - 1; dz <= WELL + 1; dz++) {
                        if (Math.abs(dx) <= WELL && Math.abs(dz) <= WELL) continue;
                        set(world, at, placement, POLE_X + dx, ty, POLE_Z + dz, LINING, limit);
                    }
                }
            }
            // 3. The pole, and the two steps of this level.
            set(world, at, placement, POLE_X, ty, POLE_Z, POLE, limit);
            int phase = Math.floorMod(ty, PERIOD);
            set(world, at, placement, BOTTOM[phase][0], ty, BOTTOM[phase][1], STEP_BOTTOM, limit);
            set(world, at, placement, TOP[phase][0], ty, TOP[phase][1], STEP_TOP, limit);
        }
        return levels;
    }

    /** Horizontal half-width of the shaft, lining included, in blocks. */
    public static int shaftHalf() {
        return WELL + 1;
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
