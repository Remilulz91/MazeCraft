package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeStyle;
import fr.mazecraft.structure.MazeTemplates;
import fr.mazecraft.structure.SlabSpiral;
import net.minecraft.block.Blocks;

/**
 * The Japanese pagoda of the cherry groves, and the maze under it.
 *
 * <p>The building is a hand-built template: the maze is a grid whose rules are arithmetic and can
 * be proved, a pagoda is not — architecture is judged by looking at it, which is the one thing
 * the code side of this project cannot do. So it was built in game, saved as a template, and is
 * only placed from here.</p>

 * <p>Its staircase was read out of that template rather than invented, and then turned out not
 * to need reading: {@link #DESCENT} states it as a direction and a phase, and {@link SlabSpiral}
 * shows that those two numbers reproduce every one of the builder's steps exactly.</p>
 *
 * <p>Numbers, and why they are what they are:</p>
 * <ul>
 *   <li><b>half 13</b> — the template is 26 by 25, so thirteen blocks either way covers it.</li>
 *   <li><b>maxRelief 8</b> — the building sits at one height, and the ground is levelled under
 *       it, so this is how much levelling is allowed rather than how flat the ground has to be.
 *       Eight is a guess and is the one number here I would not defend yet: it wants scanning,
 *       the way the maze relief rule was scanned, before anyone quotes a findability figure.</li>
 *   <li><b>clearance 4</b> — rock between the ceiling and the lowest ground. Less than this and
 *       a cave or a tree root breaks through into a corridor.</li>
 *   <li><b>floorLimit -40</b> — deepslate, and still well clear of bedrock and of the deep
 *       aquifers. Nothing goes below it whatever the terrain says.</li>
 *   <li><b>maxStair 40</b> — forty blocks of descent is eighty slab steps and ten full turns,
 *       which is already a long way down. Stated as a rule about the ground it means the pagoda
 *       may stand at most thirty blocks above the lowest point of the maze's footprint, and it
 *       is one-sided: a hill inside that footprint refuses nothing.</li>
 * </ul>
 */
public final class CherryPagoda implements Landmark {

    public static final CherryPagoda INSTANCE = new CherryPagoda();

    /** The style whose mazes this building is the way into. */
    public static final MazeStyle STYLE = MazeStyle.CHERRY;

    /** The piece placed on the surface. */
    public static final MazeTemplates.Piece PIECE = MazeTemplates.CHERRY_PAGODA;

    /**
     * Template level the player stands on when they walk in: the ground floor, where the
     * staircase starts.
     */
    public static final int GROUND_Y = 3;

    /** Height of the template, for the piece's bounding box. */
    public static final int TEMPLATE_HEIGHT = 39;

    /**
     * The staircase, as the builder drew it.
     *
     * <p>A pole of andesite wall at template (12, 12) with two polished andesite slabs winding
     * round it per level, from the ground floor to the top room — twenty-nine levels, no
     * exception, so the pattern is the rule the whole staircase obeys rather than an observation
     * about the part that was measured.</p>
     *
     * <p>{@code (-1, 3)} is not a guess and not a fit: searched over both directions and all
     * eight phases against eighty levels and both half steps of each, it is the only pair that
     * reproduces the template. Which is why a building with no staircase of its own needs
     * nothing more than a different pair here.</p>
     */
    private static final Descent DESCENT = Descent.of(12, 12, GROUND_Y,
            new SlabSpiral(-1, 3),
            Blocks.POLISHED_ANDESITE_SLAB,
            Blocks.ANDESITE_WALL.getDefaultState(),
            Blocks.POLISHED_ANDESITE.getDefaultState());

    /**
     * How far below the terrain's top solid block the template's level 0 goes.
     *
     * <p>Zero, and the first answer was one, from reading the template the wrong way. Counting
     * what each <em>layer</em> is made of says level 1 is the ground — 264 dirt, 49 grass, 43
     * dirt path, 36 concrete — and that is the fill under a raised apron, not a ground anybody
     * walks on. Counting the highest solid block of each <em>column</em> says something else:</p>
     *
     * <pre>
     *   y=0   27 columns   grass_block        the corners, where the build meets the world
     *   y=1  222 columns   garden, paths, kerb
     *   y=2  282 columns   courtyard and the building's own floor
     * </pre>
     *
     * <p>Those twenty-seven grass corners are the only cells the builder left as plain ground,
     * and they are the only ones that have to be flush, so level 0 lands on the terrain's top
     * solid block and the apron rises a block above it the way it was built to. Twenty-seven of
     * six hundred and fifty is exactly why the aggregate lied: the honest cells are a rounding
     * error in the layer counts and the whole answer in the column ones.</p>
     *
     * <p>Off by one here is a notch all the way round the footprint, so if it still looks wrong
     * {@code /maze debug pagoda <size> <lift>} places it a block up or down without a rebuild.</p>
     */
    private static final int GROUND_OFFSET = 0;

    private CherryPagoda() { }

    @Override
    public int originY(int padFloorY) {
        return padFloorY - GROUND_OFFSET;
    }

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
    public int half() {
        return 13;
    }

    @Override
    public int maxRelief() {
        // Sixteen, and every step of the way there was measured. Eight was a number I put down
        // by eye and said so. Two scans of sixty cherry groves each then said:
        //
        //   allowance      8    10    12    16    20    24    32
        //   medium sites  11    12    14    18    23    27    30
        //   large  sites   7     7     9    13    16    20    22
        //
        // Sixteen is where the curve is still steep and the cut is still a terrace: eight
        // blocks either way under a twenty-six block building, ramped over four. It takes the
        // walk to the nearest medium pagoda from 8800 blocks to 7760, and the large from 13700
        // to 11400.
        //
        // Twenty gives another thousand blocks for a ten-block cut, and twenty-four another
        // five hundred for twelve. Those are one line each, and they are a looking question:
        // the figures above say what they buy, nothing says what they look like.
        return 16;
    }

    @Override
    public int clearance() {
        return 4;
    }

    @Override
    public int floorLimit() {
        // The crypt hangs thirteen blocks under the maze floor and its skin one more, so this
        // is really a limit of -46 on the deepest block the whole thing owns -- still twelve
        // clear of the bedrock roof. A guard that cannot fire (maxStair binds first, always),
        // but a guard that is wrong by a room's height is not a guard.
        return -32;
    }

    @Override
    public int maxStair() {
        return 40;
    }
}
