package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeStyle;
import fr.mazecraft.structure.MazeTemplates;
import fr.mazecraft.structure.SlabSpiral;
import net.minecraft.block.Blocks;

/**
 * The mud-brick compound of the deserts, and the maze buried under it.
 *
 * <p>The second landmark, and the one that paid for the first one's generalisation: everything
 * here is constants and one class. Nothing in {@code ModStructures}, {@code MazeStructure} or
 * {@code MazeRepair} knows it exists.</p>
 *
 * <p>Read out of the template rather than guessed, the way the pagoda's numbers had to be in
 * the end:</p>
 * <ul>
 *   <li><b>the box is 48 × 28 × 48</b>, of which the built part is 32 by 39 (x 10..41, z 2..40)
 *       and the rest is the sand dune it stands in. The template records all 64 512 of its
 *       positions with no structure void, so placing it carves its own box and brings its own
 *       skirt of sand — which is why the cut edge needs no hiding here, unlike the pagoda's.</li>
 *   <li><b>level 0 is a complete solid layer</b>, 2 045 sand and 258 sandstone across the whole
 *       48 by 48. So {@code GROUND_OFFSET} is 0 and, unlike the pagoda, not a judgement call:
 *       there is no apron to be fooled by, the bottom of the box <em>is</em> the ground.</li>
 *   <li><b>{@link #GROUND_Y} 6</b> — the throne room's floor reads at level 5 (oak planks and
 *       red wool), so feet are at 6.</li>
 * </ul>
 */
public final class DesertPyramid implements Landmark {

    public static final DesertPyramid INSTANCE = new DesertPyramid();

    /** The style whose mazes this building is the way into. */
    public static final MazeStyle STYLE = MazeStyle.DESERT;

    /** The piece placed on the surface. */
    public static final MazeTemplates.Piece PIECE = MazeTemplates.DESERT_MUD_BASE;

    /** Template level the player stands on in the throne room: its floor is level 5. */
    public static final int GROUND_Y = 6;

    /** Height of the template's box, for the piece's bounding box. */
    public static final int TEMPLATE_HEIGHT = 28;

    /**
     * The way down, cut through the red floor of the throne room.
     *
     * <p>The red rectangle is exactly five by six — x 23..27, z 27..32, thirty cells of thirty,
     * red wool at level 5 alternating with carpet at level 6 — so a 3 × 3 centred on (25, 29)
     * sits on its axis with a clear cell either side and two blocks of air above. The throne is
     * at z 35 and the room opens towards the low z, so the player walks up the carpet towards
     * the throne and the stairwell is in their path.</p>
     *
     * <p>This building had no staircase of its own, which before {@link SlabSpiral} would have
     * been a problem and is now a choice of two numbers. {@link SlabSpiral#endingAt} picks the
     * phase that puts the top step on the side the player arrives from, {@code (0, -1)}, so the
     * stair reads as a way down rather than a hole to fall in.</p>
     *
     * <p>And the top step is at {@code GROUND_Y - 1}, not at the ground floor. The pagoda's
     * belongs at its ground floor because its own staircase carries on upward from there; here
     * there is nothing above and the shaft is a hole cut in a flat floor, so a step at the
     * ground floor stands half a block over the carpet with nothing under it. One level lower,
     * a top slab is exactly flush with the floor it is cut into.</p>
     *
     * <p>Mud brick, matching what it is cut through, so it reads as the builders' own work and
     * not as something the mod dropped in.</p>
     */
    private static final Descent DESCENT = Descent.of(25, 29, GROUND_Y, GROUND_Y - 1,
            SlabSpiral.endingAt(1, GROUND_Y - 1, new int[]{0, -1}),
            Blocks.MUD_BRICK_SLAB,
            Blocks.MUD_BRICK_WALL.getDefaultState(),
            Blocks.MUD_BRICKS.getDefaultState());

    private DesertPyramid() { }

    @Override
    public MazeStyle style() {
        return STYLE;
    }

    @Override
    public MazeTemplates.Piece piece() {
        return PIECE;
    }

    @Override
    public int templateHeight() {
        return TEMPLATE_HEIGHT;
    }

    @Override
    public Descent descent() {
        return DESCENT;
    }

    @Override
    public int originY(int padFloorY) {
        // Level 0 is a full slab of sand across the box, so it lands on the terrain's top solid
        // block and replaces it. No offset, and nothing to measure: the pagoda's one-block error
        // came from a template whose bottom layer was apron fill under a raised garden, and this
        // one has no such layer.
        return padFloorY;
    }

    @Override
    public int half() {
        // Twenty-nine, and it is not the half-box. half() is the distance from the SHAFT to the
        // furthest edge of the template, because the survey and the levelling are both centred
        // on the shaft — and this stairwell is at z 29 of a box that runs to z 0. The pagoda's
        // 13 flattered it: its pole was near the middle of a small building.
        //
        // That makes the relief rule read 59 by 59 of ground, 3481 columns against the pagoda's
        // 729. Trimming the export would barely help: the building itself is 39 deep and the
        // throne room is at one end of it, so the reach is the building's, not the box's. The
        // desert is the flattest biome in the game and far commoner than a cherry grove, so
        // this may still come out ahead — but that is a measurement, not an argument, and
        // /maze debug landmark medium desert is how it gets made.
        return 29;
    }

    @Override
    public int maxRelief() {
        // Cherry's number, as a starting point and nothing more. The scan prints the whole
        // curve (8, 10, 12, 16, 20, 24, 32) in one run, so this gets set from data the way the
        // pagoda's was rather than argued about now.
        return 16;
    }

    @Override
    public int clearance() {
        return 4;
    }

    @Override
    public int floorLimit() {
        // As the pagoda: the crypt hangs thirteen blocks under the maze floor and its skin one
        // more, so the deepest block the whole thing owns is -46. A guard that maxStair binds
        // before, always.
        return -32;
    }

    @Override
    public int maxStair() {
        return 40;
    }

    @Override
    public int maxWater() {
        // Three, as the pagoda, and it will almost never bind: the rule exists for stream banks
        // and a desert has none. An oasis is the one case, and three blocks of it on a footprint
        // this size is a puddle the levelling fills.
        return 3;
    }
}
