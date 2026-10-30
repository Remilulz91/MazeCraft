package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeTemplates;
import fr.mazecraft.structure.ModStructures;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.PagodaStair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * The pagoda on the surface, the ground levelled under it, and the shaft from its staircase down
 * to the maze.
 *
 * <p>Three jobs, and the order they happen in matters. The ground is settled first, then the
 * template is laid on it, then the staircase is carried down through both. Laying the building
 * first and levelling afterwards would mean levelling into the building.</p>
 *
 * <p>Levelling is only half the work it would be, because of something the template turned out
 * to contain: it records <b>all</b> 25 350 of its positions, 19 811 of them explicit air, and no
 * structure void. So placing it carves its own 26 by 39 by 25 box out of whatever is there — a
 * knoll standing in the footprint is removed by the building's own air, with nothing to write.
 * What is left to do is the other direction: hold the building up where the ground falls away
 * under it, and blend the cut edge into the land around it.</p>
 *
 * <p>Like {@code KronosGatePiece} this is a plain {@link StructurePiece} and not part of the
 * maze. It spans from the maze floor to thirty-nine blocks above the surface, which is nothing
 * like a maze's flat box, and keeping it separate leaves the code the other fifteen styles depend
 * on completely alone.</p>
 */
public class PagodaPiece extends StructurePiece {

    /** Width of the ring outside the footprint where the cut edge is ramped into the terrain. */
    private static final int RING = 4;

    /** How far under the building's own pad the foundation will reach for solid ground. */
    private static final int FOUNDATION = 24;

    /**
     * How far above the levelled ground standing water is cleared.
     *
     * <p>Eight, against a rule that lets three blocks of it onto the footprint: generous,
     * because what matters is the water left over the <em>raised</em> ground, and a column that
     * was three deep before the fill can be more than that at the ring's outer edge, where the
     * ramp keeps the ground low.</p>
     */
    private static final int WATERLINE = 8;

    /**
     * How far above the levelled ground to start looking for the real surface of a column.
     *
     * <p>Well past the eight blocks of relief the footprint itself is allowed, because the ring
     * reaches outside it and nothing bounds the terrain there. Truncating the search would make
     * the ramp blend towards a height the ground never had.</p>
     */
    private static final int LOOK_UP = 48;

    private final BlockPos pole;
    private final int padFloorY;
    private final int mazeFloorY;
    private final Direction facing;

    public PagodaPiece(BlockPos pole, int padFloorY, int mazeFloorY, Direction facing) {
        super(ModStructures.PAGODA_PIECE, 0, boxFor(pole, padFloorY, mazeFloorY));
        this.pole = pole;
        this.padFloorY = padFloorY;
        this.mazeFloorY = mazeFloorY;
        this.facing = facing;
        this.setOrientation(null); // absolute coordinates, no rotation
    }

    public PagodaPiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.PAGODA_PIECE, nbt);
        this.pole = new BlockPos(nbt.getInt("PoleX"), nbt.getInt("PoleY"), nbt.getInt("PoleZ"));
        this.padFloorY = nbt.getInt("PadFloorY");
        this.mazeFloorY = nbt.getInt("MazeFloorY");
        Direction d = Direction.byName(nbt.getString("Facing"));
        this.facing = d != null && d.getAxis().isHorizontal() ? d : Direction.SOUTH;
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("PoleX", pole.getX());
        nbt.putInt("PoleY", pole.getY());
        nbt.putInt("PoleZ", pole.getZ());
        nbt.putInt("PadFloorY", padFloorY);
        nbt.putInt("MazeFloorY", mazeFloorY);
        nbt.putString("Facing", facing.asString());
    }

    /**
     * The box, which decides which chunks ever call {@link #generate}.
     *
     * <p>Generous on purpose, and in blocks rather than in template coordinates: the template is
     * 26 by 25 and the pole is not its middle, so the footprint reaches further on one side than
     * the other, and which side depends on the rotation. Half the diagonal plus the ring covers
     * every orientation without having to reason about any of them. Too large costs a few
     * {@code generate} calls that write nothing; too small loses whole chunks of the building,
     * silently.</p>
     */
    private static BlockBox boxFor(BlockPos pole, int padFloorY, int mazeFloorY) {
        int reach = CherryPagoda.INSTANCE.half() + 1 + RING;
        int bottom = Math.min(mazeFloorY, padFloorY - FOUNDATION) - 1;
        int top = padFloorY + CherryPagoda.TEMPLATE_HEIGHT;
        return new BlockBox(
                pole.getX() - reach, bottom, pole.getZ() - reach,
                pole.getX() + reach, top, pole.getZ() + reach);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        MazeTemplates.Placement placement = placement(world);
        if (placement == null) return;

        level(world, chunkBox);
        MazeTemplates.place(world, placement, random, chunkBox);
        int bottomTy = CherryPagoda.GROUND_Y
                - CherryPagoda.INSTANCE.descent(padFloorY, mazeFloorY);
        // The last four levels are the maze's own walking height, between its floor and the
        // underside of its roof. They get no lining, so the stairs open into the room on all
        // four sides rather than arriving inside a sealed column standing in the middle of it.
        PagodaStair.digDown(world, placement, bottomTy, MazePiece.WALL_HEIGHT, chunkBox);
    }

    /**
     * Cuts the shaft again, after the maze's repair pass has rebuilt the rock it goes through.
     *
     * <p>This exists because of a bug that only ever appeared in a naturally generated world.
     * {@code MazeRepair} rebuilds a maze inside a chunk the first time that chunk is fully
     * loaded, to clear what neighbouring chunks' decorations spilled into it — and it rebuilt
     * only the {@link MazePiece}, which is the piece it knew about. The rebuild writes the
     * maze's own floor, roof and air over everything in its volume, so the bottom of this
     * staircase — the pole and the four levels of slabs between the maze's floor and its
     * ceiling — was quietly deleted the first time somebody walked up to the pagoda. What was
     * left was the part of the shaft <em>above</em> the maze's roof, which the maze does not
     * reach: a stub of andesite hanging out of the ceiling with no way down.</p>
     *
     * <p>None of the testing could see it. A maze placed with {@code /maze debug pagoda} is not
     * registered in the structure accessor, so {@code MazeFinder} never finds it and the repair
     * pass never runs on it; every test before this one was a placed maze.</p>
     *
     * <p>Only the shaft. The levelling and the template are the building on the surface, which
     * nothing overwrites and which a player may well have changed; re-placing them on a chunk
     * reload would undo that.</p>
     */
    public void repairShaft(StructureWorldAccess world, BlockBox chunkBox) {
        MazeTemplates.Placement placement = placement(world);
        if (placement == null) return;
        int bottomTy = CherryPagoda.GROUND_Y
                - CherryPagoda.INSTANCE.descent(padFloorY, mazeFloorY);
        PagodaStair.digDown(world, placement, bottomTy, MazePiece.WALL_HEIGHT, chunkBox);
    }

    /** Where the template sits, recomputed from the pole so it cannot drift from the shaft. */
    private MazeTemplates.Placement placement(StructureWorldAccess world) {
        var templates = world.toServerWorld().getStructureTemplateManager();
        BlockPos stairTop = new BlockPos(pole.getX(),
                CherryPagoda.INSTANCE.originY(padFloorY), pole.getZ());
        BlockPos at = MazeTemplates.centreFor(templates, CherryPagoda.PIECE, facing,
                PagodaStair.POLE_X, PagodaStair.POLE_Z, stairTop);
        return MazeTemplates.placement(templates, CherryPagoda.PIECE, at, facing);
    }

    /**
     * Holds the building up, and ramps the cut edge into the land.
     *
     * <p>Inside the footprint there is nothing to cut — the template's own air does that — so all
     * that is needed is filling under it where the ground falls away. Outside, for {@code RING}
     * blocks, the ground is moved towards the building's level in a straight ramp: at the edge it
     * matches the pagoda, at the far side of the ring it is the terrain untouched. Without it a
     * site with eight blocks of relief leaves an eight-block vertical face of dirt exactly along
     * the line where the template's box ends, which is the one place a cut is most obvious.</p>
     *
     * <p>Heights are found by reading blocks downwards rather than by asking the generator, the
     * way the maze's own foundation pass does. A {@code getHeight} builds a whole NoiseChunk for
     * one column; the blocks are already there by the time a piece generates.</p>
     */
    private void level(StructureWorldAccess world, BlockBox chunkBox) {
        int foot = CherryPagoda.INSTANCE.half();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -foot - RING; dx <= foot + RING; dx++) {
            for (int dz = -foot - RING; dz <= foot + RING; dz++) {
                int x = pole.getX() + dx;
                int z = pole.getZ() + dz;
                // Nothing in this column is in this chunk: skip it before reading any blocks.
                if (x < chunkBox.getMinX() || x > chunkBox.getMaxX()
                        || z < chunkBox.getMinZ() || z > chunkBox.getMaxZ()) {
                    continue;
                }
                int out = Math.max(0, Math.max(Math.abs(dx), Math.abs(dz)) - foot);
                int natural = solidTop(world, pos, x, z);
                int target = out == 0 ? padFloorY
                        : padFloorY + (natural - padFloorY) * out / (RING + 1);
                BlockState cap = world.getBlockState(pos.set(x, natural, z));
                BlockState fill = world.getBlockState(pos.set(x, natural - 1, z));
                if (fill.isAir() || !fill.getFluidState().isEmpty()) {
                    fill = Blocks.DIRT.getDefaultState();
                }
                // Down: cut whatever stands above where the ground is going.
                for (int y = natural; y > target; y--) {
                    put(world, pos, x, y, z, Blocks.AIR.getDefaultState(), chunkBox);
                }
                // Up: fill to it, so nothing is left standing on air.
                for (int y = natural + 1; y <= target; y++) {
                    put(world, pos, x, y, z, fill, chunkBox);
                }
                // And the surface block the column had, back on top.
                if (target != natural) put(world, pos, x, target, z, cap, chunkBox);
                // Then take the water off it.
                //
                // Raising the ground out of a pond leaves the rest of the pond standing on the
                // new ground, and the building's own air is written straight into it. Allowing
                // three blocks of water on the footprint without this would have meant a
                // courtyard with a stream running through it - which is not what "the levelling
                // fills it" promised. Sources inside the cleared area go with it; a pond that
                // reaches past the ring can still seep back, and a site that deep is refused.
                for (int y = target + 1; y <= target + WATERLINE; y++) {
                    pos.set(x, y, z);
                    if (world.getBlockState(pos).getFluidState().isEmpty()) continue;
                    put(world, pos, x, y, z, Blocks.AIR.getDefaultState(), chunkBox);
                }
                // Under the building's own pad, reach for solid ground.
                if (out == 0) {
                    for (int y = target - 1; y >= target - FOUNDATION; y--) {
                        BlockState current = world.getBlockState(pos.set(x, y, z));
                        if (!current.isAir() && !current.isReplaceable()
                                && current.getFluidState().isEmpty()) {
                            break;
                        }
                        put(world, pos, x, y, z, fill, chunkBox);
                    }
                }
            }
        }
    }

    /** Highest solid block of this column, looked for from well above the levelled ground. */
    private int solidTop(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z) {
        int from = padFloorY + LOOK_UP;
        for (int y = from; y > padFloorY - FOUNDATION; y--) {
            BlockState state = world.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty()) {
                return y;
            }
        }
        return padFloorY;
    }

    private static void put(StructureWorldAccess world, BlockPos.Mutable pos, int x, int y, int z,
                            BlockState state, BlockBox chunkBox) {
        pos.set(x, y, z);
        if (!chunkBox.contains(pos)) return;
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
