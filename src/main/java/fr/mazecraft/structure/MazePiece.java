package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
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

/**
 * The whole maze as a single structure piece.
 *
 * Minecraft calls {@link #generate} once per chunk the bounding box overlaps, with
 * {@code chunkBox} limited to that chunk. The layout is rebuilt from the stored seed
 * each time, so every chunk agrees on the maze, and we only ever write inside
 * {@code chunkBox} — required to stay safe when neighbouring chunks are not generated yet.
 *
 * The bounding box includes a flattened {@link #MARGIN}-block ring around the maze, so the
 * entrance is never buried in a slope and no tree grows against the hedges.
 *
 * The structure is generated at the LAST decoration step (top_layer_modification), after
 * trees and plants of the same chunk: anything they put inside the box is cleared.
 */
public class MazePiece extends StructurePiece {

    /** Height of the walls above the floor. */
    public static final int WALL_HEIGHT = 4;
    /** Blocks cleared above the floor (removes hills / overhangs inside the maze). */
    public static final int CLEAR_HEIGHT = 10;
    /** Maximum depth filled with foundation under the floor on uneven ground. */
    public static final int FOUNDATION_DEPTH = 12;
    /**
     * Flattened ring around the maze (blocks). Its floor is the corridor floor (dirt path),
     * so no tree can grow close enough for its canopy to spill into the corridors.
     */
    public static final int MARGIN = 5;

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
        super(ModStructures.MAZE_PIECE, 0, boxFor(size, centerX, floorY, centerZ));
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

    private static BlockBox boxFor(MazeSize size, int centerX, int floorY, int centerZ) {
        int half = size.span() / 2;
        int minX = centerX - half - MARGIN;
        int minZ = centerZ - half - MARGIN;
        int side = size.span() + 2 * MARGIN;
        return new BlockBox(
                minX, floorY - FOUNDATION_DEPTH, minZ,
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
            layout = MazeLayout.generate(size.cells(), seed, entranceSide);
        }
        return layout;
    }

    /** World X of local maze coordinate 0 (the maze proper, not the margin). */
    private int originX() {
        return boundingBox.getMinX() + MARGIN;
    }

    private int originZ() {
        return boundingBox.getMinZ() + MARGIN;
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

        // 4. Central chest
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
