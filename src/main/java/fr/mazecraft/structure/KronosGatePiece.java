package fr.mazecraft.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesHolder;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * The way down to the Labyrinth of Kronos: a ruin on the surface and the shaft under it.
 *
 * <p>The vault itself is buried forty blocks down and would never be found by walking around,
 * so its entrance is advertised: a small weathered ruin sits directly above it, open from the
 * first day of a world. Climbing down leads to the vault's own doorway, which stays sealed until
 * every maze of every style has been conquered. Finding the door long before being able to open
 * it is the point.</p>
 *
 * <p>This is a plain {@link StructurePiece} rather than part of the maze: it spans from the
 * vault floor to the sky, which is nothing like a maze's flat box, and keeping it separate means
 * the maze code that the sixteen ordinary styles depend on is left completely alone.</p>
 */
public class KronosGatePiece extends StructurePiece {

    /** Inner width of the shaft (3 × 3 of air). */
    private static final int SHAFT_RADIUS = 1;

    /** Height of the ruin's columns above ground. */
    private static final int RUIN_HEIGHT = 4;

    /** Half-width of the ruin's floor slab. */
    private static final int RUIN_RADIUS = 4;

    /**
     * The eight cells around the edge of the shaft, in order. One step per block of rise makes
     * a spiral staircase — the ordinary way to climb a 3 × 3 shaft in Minecraft.
     *
     * <p>The ring is always walked in this order, but <em>where it starts</em> depends on which
     * way the doorway faces: see {@link #spiralStart()}.</p>
     */
    private static final int[][] SPIRAL = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    private final BlockPos foot;
    private final int surfaceY;
    /** Horizontal direction from the foot of the shaft towards the vault's doorway. */
    private final Direction toVault;

    public KronosGatePiece(BlockPos foot, int surfaceY, Direction toVault) {
        super(ModStructures.KRONOS_GATE_PIECE, 0, boxFor(foot, surfaceY));
        this.foot = foot;
        this.surfaceY = surfaceY;
        this.toVault = toVault;
        this.setOrientation(null); // absolute coordinates, no rotation
    }

    public KronosGatePiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.KRONOS_GATE_PIECE, nbt);
        this.foot = new BlockPos(nbt.getInt("FootX"), nbt.getInt("FootY"), nbt.getInt("FootZ"));
        this.surfaceY = nbt.getInt("SurfaceY");
        Direction d = Direction.byName(nbt.getString("ToVault"));
        this.toVault = d != null && d.getAxis().isHorizontal() ? d : Direction.NORTH;
    }

    /**
     * Where the spiral begins: the cell of the ring that sits immediately inside the doorway.
     *
     * <p>The ring order is fixed, but the doorway can be on any of the four sides. Starting the
     * spiral at a fixed corner meant that for three orientations out of four the lowest step was
     * on the far side of the shaft — a player coming back out of the vault faced a full block
     * with nothing to step on, and had to place one to get home. Starting it at the doorway puts
     * the first half-step directly under the foot that walks in.</p>
     */
    private int spiralStart() {
        for (int i = 0; i < SPIRAL.length; i++) {
            if (SPIRAL[i][0] == toVault.getOffsetX() && SPIRAL[i][1] == toVault.getOffsetZ()) return i;
        }
        return 0; // toVault is always horizontal, so this is unreachable
    }

    private static BlockBox boxFor(BlockPos foot, int surfaceY) {
        int r = RUIN_RADIUS + 1;
        return new BlockBox(
                foot.getX() - r, foot.getY() - 1, foot.getZ() - r,
                foot.getX() + r, surfaceY + RUIN_HEIGHT + 1, foot.getZ() + r);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("FootX", foot.getX());
        nbt.putInt("FootY", foot.getY());
        nbt.putInt("FootZ", foot.getZ());
        nbt.putInt("SurfaceY", surfaceY);
        nbt.putString("ToVault", toVault.asString());
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        BlockState bronze = Blocks.OXIDIZED_COPPER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int top = surfaceY + RUIN_HEIGHT;

        // --- The shaft: a 3×3 well from the vault doorway up to the surface, lined with brick
        //     so it never opens into a cave on the way, with a spiral stair to climb back out.
        //
        //     The stair is made of blocks that stand on their own. Ladders were tried first and
        //     popped off as dropped items: a ladder needs the wall behind it to already exist,
        //     and a structure is built one chunk at a time, so wherever a chunk boundary ran
        //     between a ladder and its wall the ladder was placed against nothing.
        for (int y = foot.getY() - 1; y <= surfaceY; y++) {
            for (int dx = -SHAFT_RADIUS - 1; dx <= SHAFT_RADIUS + 1; dx++) {
                for (int dz = -SHAFT_RADIUS - 1; dz <= SHAFT_RADIUS + 1; dz++) {
                    boolean lining = Math.abs(dx) > SHAFT_RADIUS || Math.abs(dz) > SHAFT_RADIUS;
                    pos.set(foot.getX() + dx, y, foot.getZ() + dz);
                    if (!chunkBox.contains(pos)) continue;
                    // Leave the foot of the shaft open towards the vault, or the climb down ends
                    // in a sealed brick box with the door walled off behind it.
                    // Exactly the face that looks at the vault, not the corners beside it.
                    int along = dx * toVault.getOffsetX() + dz * toVault.getOffsetZ();
                    int across = dx * toVault.getOffsetZ() + dz * toVault.getOffsetX();
                    boolean doorway = lining && y >= foot.getY() && y <= foot.getY() + 2
                            && along == SHAFT_RADIUS + 1 && Math.abs(across) <= SHAFT_RADIUS;
                    if (doorway) {
                        world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
                    } else if (lining) {
                        world.setBlockState(pos, random.nextInt(4) == 0 ? cracked : wall, Block.NOTIFY_LISTENERS);
                    } else if (y > foot.getY() - 1) {
                        world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(pos, wall, Block.NOTIFY_LISTENERS);
                    }
                }
            }
            // Two steps of the spiral per block of rise — a bottom slab then a top slab, so
            // each step is half a block and the whole climb is walked, never jumped. Full blocks
            // one apart would mean jumping forty times to get out.
            // Starts at the shaft floor itself, in the cell just inside the doorway: a first step
            // one block up would be a block and a half above where the player is standing, and a
            // first step on the far side of the shaft would mean walking in from the vault and
            // finding nothing to climb.
            if (y >= foot.getY() && y <= surfaceY) {
                for (int half = 0; half < 2; half++) {
                    int index = (y - foot.getY()) * 2 + half + spiralStart();
                    int[] step = SPIRAL[Math.floorMod(index, SPIRAL.length)];
                    pos.set(foot.getX() + step[0], y, foot.getZ() + step[1]);
                    if (!chunkBox.contains(pos)) continue;
                    world.setBlockState(pos, Blocks.DEEPSLATE_BRICK_SLAB.getDefaultState()
                            .with(SlabBlock.TYPE, half == 0 ? SlabType.BOTTOM : SlabType.TOP),
                            Block.NOTIFY_LISTENERS);
                }
            }
        }

        // --- The ruin: a well-head. A cracked apron at ground level, four posts at the corners,
        //     a roof across them, and the lantern hanging underneath it rather than floating in
        //     mid-air. The mouth of the shaft is left open in the middle.
        for (int dx = -RUIN_RADIUS; dx <= RUIN_RADIUS; dx++) {
            for (int dz = -RUIN_RADIUS; dz <= RUIN_RADIUS; dz++) {
                if (Math.abs(dx) <= SHAFT_RADIUS && Math.abs(dz) <= SHAFT_RADIUS) continue; // the mouth
                pos.set(foot.getX() + dx, surfaceY, foot.getZ() + dz);
                if (!chunkBox.contains(pos)) continue;
                boolean edge = Math.abs(dx) == RUIN_RADIUS || Math.abs(dz) == RUIN_RADIUS;
                // The rim is left broken on purpose: a ruin, not a monument.
                if (edge && random.nextInt(3) == 0) continue;
                world.setBlockState(pos, random.nextInt(3) == 0 ? cracked : wall, Block.NOTIFY_LISTENERS);
                for (int dy = 1; dy <= RUIN_HEIGHT + 1; dy++) {
                    pos.set(foot.getX() + dx, surfaceY + dy, foot.getZ() + dz);
                    if (chunkBox.contains(pos)) world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
                }
            }
        }

        int c = SHAFT_RADIUS + 1; // the posts stand just clear of the mouth
        for (int[] corner : new int[][]{{-c, -c}, {-c, c}, {c, -c}, {c, c}}) {
            for (int dy = 1; dy < RUIN_HEIGHT; dy++) {
                pos.set(foot.getX() + corner[0], surfaceY + dy, foot.getZ() + corner[1]);
                if (!chunkBox.contains(pos)) continue;
                world.setBlockState(pos, dy == RUIN_HEIGHT - 1 ? bronze : wall, Block.NOTIFY_LISTENERS);
            }
        }

        // The roof the posts carry, one block wider than they stand.
        for (int dx = -c; dx <= c; dx++) {
            for (int dz = -c; dz <= c; dz++) {
                pos.set(foot.getX() + dx, top, foot.getZ() + dz);
                if (!chunkBox.contains(pos)) continue;
                boolean rim = Math.abs(dx) == c || Math.abs(dz) == c;
                world.setBlockState(pos, rim ? bronze : (random.nextInt(4) == 0 ? cracked : wall),
                        Block.NOTIFY_LISTENERS);
            }
        }

        // And the lantern, hanging from it over the mouth.
        pos.set(foot.getX(), top - 1, foot.getZ());
        if (chunkBox.contains(pos)) {
            world.setBlockState(pos, Blocks.SOUL_LANTERN.getDefaultState()
                    .with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
        }
    }
}
