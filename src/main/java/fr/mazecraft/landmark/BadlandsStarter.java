package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeStyle;
import fr.mazecraft.structure.MazeTemplates;
import fr.mazecraft.structure.SlabSpiral;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * The starter house of the badlands, and the maze buried under it.
 *
 * <p>Chosen over the other badlands build on one number. Both are about the same size and both
 * would do; but this one's hall is a clear twelve by five that straddles the middle of its box,
 * so the shaft can sit at (13, 6) and {@link #half()} comes out at <b>14</b> — the smallest a
 * box 28 wide allows. The other house sits off-centre in its box, so its shaft would have cost
 * 14 to 16 and never less. Fourteen puts this building level with the pagoda's 13, against the
 * desert's 29, which matters here more than anywhere: the badlands is the roughest ground any
 * of these buildings has to stand on.</p>
 *
 * <p>Read out of the template:</p>
 * <ul>
 *   <li><b>level 0 is a complete layer</b> — 295 red sand and 69 oak logs across the whole
 *       28 by 13 — so {@code GROUND_OFFSET} is 0 with nothing to weigh up.</li>
 *   <li><b>the house's floor IS that layer</b>: the walls start at level 1 and the hall is open
 *       from there, so feet are at 1 and {@link #GROUND_Y} is 1.</li>
 * </ul>
 */
public final class BadlandsStarter implements Landmark {

    public static final BadlandsStarter INSTANCE = new BadlandsStarter();

    /** The style whose mazes this building is the way into. */
    public static final MazeStyle STYLE = MazeStyle.BADLANDS;

    /** The piece placed on the surface. */
    public static final MazeTemplates.Piece PIECE = MazeTemplates.BADLANDS_STARTER_HOUSE;

    /** Template level the player stands on: the floor is level 0, so feet are at 1. */
    public static final int GROUND_Y = 1;

    /** Height of the template's box, for the piece's bounding box. */
    public static final int TEMPLATE_HEIGHT = 14;

    /**
     * The way down, cut through the floor of the hall.
     *
     * <p>(13, 6) is the only spot that is both clear on three levels with a block of margin
     * all round, and on the middle of the box: the hall runs x 5..16 by z 4..8 and the box is
     * 28 by 13, so this is where the two agree. Moving it one block either way costs a block
     * of {@code half()} and buys nothing.</p>
     *
     * <p>Oak and terracotta, which is what the house is made of. The top step is at
     * {@code GROUND_Y - 1} — level 0 — so it is flush with the floor it is cut into rather
     * than standing over it; {@link Descent#topStep} says why that is not the same as the
     * pagoda's.</p>
     */
    private static final Descent DESCENT = Descent.of(13, 6, GROUND_Y, GROUND_Y - 1,
            SlabSpiral.endingAt(1, GROUND_Y - 1, new int[]{0, -1}),
            Blocks.OAK_SLAB,
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.ORANGE_TERRACOTTA.getDefaultState());

    private BadlandsStarter() { }

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

    /**
     * Red sand is this building's stand-in for the ground, and the one landmark so far that
     * needs one: a badlands is red sand in the flats and banded terracotta on the plateaus.
     * See {@link Landmark#blend()}.
     */
    @Override
    public Block blend() {
        return Blocks.RED_SAND;
    }

    @Override
    public int originY(int padFloorY) {
        // Level 0 is a full layer of ground across the box, so it lands on the terrain's top
        // solid block and replaces it.
        return padFloorY;
    }

    @Override
    public int half() {
        // The distance from the shaft at (13, 6) to the furthest edge of a 28 by 13 box: 14 on
        // the x axis, 6 on the z. The box decides it, and 14 is its floor.
        return 14;
    }

    @Override
    public int maxRelief() {
        // The same 16 the other two settled on, as a starting point. The badlands is the
        // roughest terrain of the remaining six, so this is the one biome where the number may
        // genuinely have to move -- /maze debug landmark medium badlands prints the whole curve
        // in one run and will say so.
        return 16;
    }

    @Override
    public int clearance() {
        return 4;
    }

    @Override
    public int floorLimit() {
        return -32;
    }

    @Override
    public int maxStair() {
        return 40;
    }

    @Override
    public int maxWater() {
        // Three, as the others. A badlands has next to no surface water, so like the desert's
        // this will almost never be the rule that refuses a site.
        return 3;
    }
}
