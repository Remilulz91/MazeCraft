package fr.mazecraft.landmark;

import fr.mazecraft.structure.SlabSpiral;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;

/**
 * Where a building's way down is cut, and what it is made of.
 *
 * <p>Everything in here is in <b>template coordinates</b> — the building's own grid, before any
 * rotation — because that is the only frame in which it can be stated once and stay true for all
 * four facings. Turning it into world positions is {@code StructureTemplate.transform}'s job,
 * the same call that lays every block of the template itself.</p>
 *
 * <p>A building with a staircase already built into it, like the cherry pagoda, states the
 * spiral it was drawn with. A building without one states a spiral of its own choosing and gets
 * the same stair; {@link SlabSpiral} explains why those are the same object.</p>
 *
 * @param x        column the shaft is cut down, in template coordinates
 * @param z        the same, on the other axis
 * @param groundY  template level the player stands on walking in: where the descent starts
 * @param spiral   how the steps wind round the pole
 */
public record Descent(int x, int z, int groundY, SlabSpiral spiral,
                      BlockState stepBottom, BlockState stepTop,
                      BlockState pole, BlockState lining) {

    /** The usual way to state one: a slab block, a pole and a lining. */
    public static Descent of(int x, int z, int groundY, SlabSpiral spiral,
                             Block slab, BlockState pole, BlockState lining) {
        return new Descent(x, z, groundY, spiral,
                slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                slab.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                pole, lining);
    }

    /** The step at this half of this level, or null when this cell carries none. */
    public BlockState stepAt(int level, int half, int dx, int dz) {
        if (!spiral.carries(level, half, dx, dz)) return null;
        return half == 0 ? stepBottom : stepTop;
    }
}
