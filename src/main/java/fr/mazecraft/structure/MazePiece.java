package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The whole maze as a single structure piece.
 *
 * Minecraft calls {@link #generate} once per chunk the bounding box overlaps, with
 * {@code chunkBox} limited to that chunk. The layout is rebuilt from the stored seed
 * each time, so every chunk agrees on the maze, and we only ever write inside
 * {@code chunkBox} — required to stay safe when neighbouring chunks are not generated yet.
 *
 * The bounding box includes a flattened ring ({@link MazeStyle#margin} blocks) around the maze, so the
 * entrance is never buried in a slope and no tree grows against the hedges.
 *
 * The structure is generated at the surface_structures step, BEFORE trees and plants: its
 * dirt-path floor (maze + ring) is not a valid soil, so nothing can grow on or next to it.
 */
public class MazePiece extends StructurePiece {

    /** Height of the walls above the floor. */
    public static final int WALL_HEIGHT = 4;
    /** Blocks cleared above the floor (removes hills / overhangs inside the maze). */
    public static final int CLEAR_HEIGHT = 10;
    /** Maximum depth filled with foundation under the floor on uneven ground. */
    public static final int FOUNDATION_DEPTH = 12;
    /** Height of the levers above the floor. */
    public static final int LEVER_DY = 2;

    private final MazeStyle style;
    private final MazeSize size;
    private final long seed;
    private final int floorY;
    private final int entranceSide;

    // Rebuilt lazily from the seed; not saved to NBT
    private MazeLayout layout;

    /**
     * @param entranceSide {@link MazeLayout#NORTH}.. {@link MazeLayout#EAST}, or -1 for random
     */
    public MazePiece(MazeStyle style, MazeSize size, long seed, int centerX, int floorY, int centerZ, int entranceSide) {
        super(ModStructures.MAZE_PIECE, 0, boxFor(style, size, centerX, floorY, centerZ));
        this.style = style;
        this.size = size;
        this.seed = seed;
        this.floorY = floorY;
        this.entranceSide = entranceSide;
        this.setOrientation(null); // absolute coordinates, no rotation
    }

    /** Loads a piece from its saved NBT (chunks saved but not fully generated yet). */
    public MazePiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.MAZE_PIECE, nbt);
        MazeStyle s = MazeStyle.fromId(nbt.getString("Style"));
        MazeSize z = MazeSize.fromId(nbt.getString("Size"));
        this.style = s != null ? s : MazeStyle.HEDGE;
        this.size = z != null ? z : MazeSize.SMALL;
        this.seed = nbt.getLong("Seed");
        this.floorY = nbt.getInt("FloorY");
        this.entranceSide = nbt.contains("Entrance") ? nbt.getInt("Entrance") : -1;
    }

    private static BlockBox boxFor(MazeStyle style, MazeSize size, int centerX, int floorY, int centerZ) {
        int half = size.span() / 2;
        int minX = centerX - half - style.margin;
        int minZ = centerZ - half - style.margin;
        int side = size.span() + 2 * style.margin;
        // The box starts just ABOVE the floor on purpose: with terrain_adaptation "beard_thin",
        // Minecraft makes the ground solid up to the box bottom (so up to floorY, flush with our
        // floor) and clears above it, with a smooth slope around the box (like villages).
        // A box starting below the floor made it carve a huge flat cavern around the maze.
        // The floor and foundation are still written (inside the chunk) and still protected
        // (see isProtected / MazeFinder), they just aren't part of the box.
        return new BlockBox(
                minX, floorY + 1, minZ,
                minX + side - 1, floorY + CLEAR_HEIGHT, minZ + side - 1);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putString("Style", style.id());
        nbt.putString("Size", size.id());
        nbt.putLong("Seed", seed);
        nbt.putInt("FloorY", floorY);
        nbt.putInt("Entrance", entranceSide);
    }

    public MazeLayout layout() {
        if (layout == null) {
            layout = MazeLayout.generate(size.cells(), seed, entranceSide, size.gates());
        }
        return layout;
    }

    /** World X of local maze coordinate 0 (the maze proper, not the margin). */
    private int originX() {
        return boundingBox.getMinX() + style.margin;
    }

    private int originZ() {
        return boundingBox.getMinZ() + style.margin;
    }

    /** World position of the central chest. */
    public BlockPos chestPos() {
        int c = layout().center();
        return new BlockPos(originX() + c, floorY + 1, originZ() + c);
    }

    /** World position (feet) just outside the entrance, on the approach path. */
    public BlockPos entranceOutsidePos() {
        int[] l = layout().entranceOutside(2);
        return new BlockPos(originX() + l[0], floorY + 1, originZ() + l[1]);
    }

    /** World positions of all blocks of gate {@code index} (3 wide × WALL_HEIGHT high). */
    public List<BlockPos> gateBlocks(int index) {
        List<BlockPos> list = new ArrayList<>();
        MazeLayout.Gate gate = layout().gates().get(index);
        for (int x = gate.x0(); x <= gate.x1(); x++) {
            for (int z = gate.z0(); z <= gate.z1(); z++) {
                for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                    list.add(new BlockPos(originX() + x, floorY + dy, originZ() + z));
                }
            }
        }
        return list;
    }

    public int gateCount() {
        return layout().gates().size();
    }

    /** World position of lever {@code index}. */
    public BlockPos leverPos(int index) {
        MazeLayout.Lever lever = layout().levers().get(index);
        return new BlockPos(originX() + lever.x(), floorY + LEVER_DY, originZ() + lever.z());
    }

    /** Index of the lever at this position, or -1. */
    public int leverIndexAt(BlockPos pos) {
        for (int i = 0; i < layout().levers().size(); i++) {
            if (leverPos(i).equals(pos)) return i;
        }
        return -1;
    }

    /** Blocks protected by this maze: its box, plus the floor and foundation below it. */
    public boolean isProtected(BlockPos pos) {
        return pos.getX() >= boundingBox.getMinX() && pos.getX() <= boundingBox.getMaxX()
                && pos.getZ() >= boundingBox.getMinZ() && pos.getZ() <= boundingBox.getMaxZ()
                && pos.getY() >= floorY - FOUNDATION_DEPTH && pos.getY() <= boundingBox.getMaxY();
    }

    /** True if (x, z) is inside the maze proper (not the margin). */
    public boolean isInsideMaze(int x, int z) {
        return layout().isInside(x - originX(), z - originZ());
    }

    /** Loot table of the central chest for this maze size. */
    public static RegistryKey<LootTable> lootTableFor(MazeSize size) {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, MazeCraft.id("chests/maze_" + size.id()));
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        MazeLayout layout = layout();
        BlockBox box = this.boundingBox;

        int x0 = Math.max(box.getMinX(), chunkBox.getMinX());
        int x1 = Math.min(box.getMaxX(), chunkBox.getMaxX());
        int z0 = Math.max(box.getMinZ(), chunkBox.getMinZ());
        int z1 = Math.min(box.getMaxZ(), chunkBox.getMaxZ());
        if (x0 > x1 || z0 > z1) return;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int lx = x - originX();
                int lz = z - originZ();
                boolean inside = layout.isInside(lx, lz);
                boolean isWall = inside && layout.isWall(lx, lz);
                boolean isPillar = inside && layout.isPillar(lx, lz);
                boolean isPlaza = inside && layout.isPlaza(lx, lz);

                // 1. Foundation: fill air / fluids / plants under the floor until solid ground
                for (int y = floorY - 1; y >= floorY - FOUNDATION_DEPTH; y--) {
                    pos.set(x, y, z);
                    BlockState current = world.getBlockState(pos);
                    if (!current.isAir() && !current.isReplaceable() && current.getFluidState().isEmpty()) {
                        break;
                    }
                    world.setBlockState(pos, style.foundation, Block.NOTIFY_LISTENERS);
                }

                // 2. Floor
                BlockState floor;
                if (!inside) {
                    floor = style.floor; // margin ring: no dirt/grass → no trees next to the hedges
                } else if (isWall) {
                    floor = style.wallBase;
                } else {
                    floor = isPlaza ? style.plaza : style.floor;
                }
                world.setBlockState(pos.set(x, floorY, z), floor, Block.NOTIFY_LISTENERS);

                // 3. Walls, pillars, and clearing above
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    pos.set(x, floorY + dy, z);
                    BlockState target;
                    if (isWall && dy <= WALL_HEIGHT) {
                        target = isPillar ? style.pillar : style.wall;
                    } else if (isPillar && dy == WALL_HEIGHT + 1 && lx % 8 == 0 && lz % 8 == 0) {
                        target = style.light;
                    } else {
                        target = air;
                    }
                    if (world.getBlockState(pos) != target) {
                        world.setBlockState(pos, target, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 4. Gates (closed) — bars connected along the opening so nobody squeezes through
        for (MazeLayout.Gate gate : layout.gates()) {
            BlockState bars = style.gate;
            if (bars.contains(HorizontalConnectingBlock.NORTH)) {
                bars = gate.alongX()
                        ? bars.with(HorizontalConnectingBlock.EAST, true).with(HorizontalConnectingBlock.WEST, true)
                        : bars.with(HorizontalConnectingBlock.NORTH, true).with(HorizontalConnectingBlock.SOUTH, true);
            }
            for (int gx = gate.x0(); gx <= gate.x1(); gx++) {
                for (int gz = gate.z0(); gz <= gate.z1(); gz++) {
                    for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                        pos.set(originX() + gx, floorY + dy, originZ() + gz);
                        if (chunkBox.contains(pos)) world.setBlockState(pos, bars, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 5. Levers, each hanging on a log set into the hedge (leaves can't hold a lever)
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (MazeLayout.Lever lever : layout.levers()) {
            pos.set(originX() + lever.supportX(), floorY + LEVER_DY, originZ() + lever.supportZ());
            if (chunkBox.contains(pos)) world.setBlockState(pos, style.pillar, Block.NOTIFY_LISTENERS);
            pos.set(originX() + lever.x(), floorY + LEVER_DY, originZ() + lever.z());
            if (chunkBox.contains(pos)) {
                world.setBlockState(pos, Blocks.LEVER.getDefaultState()
                        .with(LeverBlock.FACE, BlockFace.WALL)
                        .with(LeverBlock.FACING, facings[lever.facing()]), Block.NOTIFY_LISTENERS);
            }
        }

        // 6. Central chest
        BlockPos chest = chestPos();
        if (chunkBox.contains(chest)) {
            world.setBlockState(chest,
                    Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            BlockEntity be = world.getBlockEntity(chest);
            if (be instanceof ChestBlockEntity chestEntity) {
                chestEntity.setLootTable(lootTableFor(size), random.nextLong());
            }
        }
    }

    public MazeStyle getStyle() {
        return style;
    }

    public MazeSize getSize() {
        return size;
    }

    public int getFloorY() {
        return floorY;
    }
}
