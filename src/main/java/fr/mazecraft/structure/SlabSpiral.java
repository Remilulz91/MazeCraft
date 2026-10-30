package fr.mazecraft.structure;

/**
 * A staircase of slabs winding round the pole of a 3 × 3 shaft, two half steps per block of
 * height.
 *
 * <p>This exists because two things that were written months apart turned out to be the same
 * thing. The crypt's stair was generated: eight cells round a 3 × 3, one step per half block,
 * a phase chosen so the top step lands under the doorway. The pagoda's was <em>built by hand</em>
 * and read back out of the template — three attempts, because it is slabs and not stairs, and
 * the first two detectors went looking for {@code *_stairs} blocks and for straight runs. What
 * came out was a table of four phases, which looked like a fact about that particular
 * building.</p>
 *
 * <p>It is not. Written as an offset from the pole, the pagoda's four phases are the eight cells
 * of the ring, in order, once per four levels — the same turn the crypt's stair makes, going the
 * other way round and starting three cells along:</p>
 *
 * <pre>
 *   cell = RING[(phase + direction × (level × 2 + half)) mod 8]
 *
 *   the pagoda's staircase     direction −1, phase 3
 *   the crypt's stair          direction +1, phase chosen per maze
 * </pre>
 *
 * <p>{@code (−1, 3)} is the <b>only</b> pair that fits: searched over both directions and all
 * eight phases, against eighty levels and both half steps of each, including the negative levels
 * the shaft continues into below the ground floor. So this is not a formula that happens to
 * match the part that was measured, it is what the builder drew.</p>
 *
 * <p>Which means a building that has no staircase of its own needs none: pick a direction and a
 * phase and the descent is the same object the pagoda has.</p>
 */
public final class SlabSpiral {

    /**
     * The eight cells round the pole, in order, as offsets from it.
     *
     * <p>Clockwise seen from above, starting due east. One step per half block of height means a
     * full turn every four levels, which is what makes it walkable: half a block up per step.</p>
     */
    public static final int[][] RING = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    /** Half steps per block of height: a bottom slab, then a top slab. */
    public static final int HALVES = 2;

    private final int direction;
    private final int phase;

    /**
     * @param direction +1 to wind clockwise seen from above, −1 for the other way
     * @param phase     which cell of the ring the half step at level 0 lands on
     */
    public SlabSpiral(int direction, int phase) {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("direction must be +1 or -1, not " + direction);
        }
        this.direction = direction;
        this.phase = phase;
    }

    /**
     * The cell this half step stands in, as an offset from the pole.
     *
     * <p>Keyed on the level itself rather than on a count from the bottom, so continuing the
     * staircase downwards needs no special case at all: {@code floorMod} of a negative level
     * gives the phase that carries straight on from the level above it.</p>
     *
     * @param half 0 for the bottom slab of this level, 1 for the top slab
     */
    public int[] cell(int level, int half) {
        return RING[Math.floorMod(phase + direction * (level * HALVES + half), RING.length)];
    }

    /** True when the cell at this offset carries this level's half step. */
    public boolean carries(int level, int half, int dx, int dz) {
        int[] c = cell(level, half);
        return c[0] == dx && c[1] == dz;
    }

    /**
     * The spiral turned so that the <em>last</em> half step of {@code level} lands on
     * {@code towards} — the cell of the doorway the stair has to arrive at.
     *
     * <p>A stair that comes out facing a wall is a stair you walk down and then turn round in.
     * The crypt's does this so its top step is under the dead end's one exit.</p>
     */
    public static SlabSpiral endingAt(int direction, int level, int[] towards) {
        int target = 0;
        for (int i = 0; i < RING.length; i++) {
            if (RING[i][0] == towards[0] && RING[i][1] == towards[1]) target = i;
        }
        // Solve (phase + direction * last) ≡ target, where last is that level's top half step.
        int last = level * HALVES + 1;
        return new SlabSpiral(direction, Math.floorMod(target - direction * last, RING.length));
    }

    public int direction() {
        return direction;
    }

    public int phase() {
        return phase;
    }
}
