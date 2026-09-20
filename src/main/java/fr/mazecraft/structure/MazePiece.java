package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.enemy.MazeEnemies;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
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
import java.util.function.IntPredicate;

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
    /** Enclosed mazes (Nether): the roof sits right on top of the walls. */
    public static final int ROOF_DY = WALL_HEIGHT + 1;
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
                minX + side - 1, floorY + (style.enclosed ? ROOF_DY : CLEAR_HEIGHT), minZ + side - 1);
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

    /**
     * Ariadne's thread: shortest path through the corridors (block by block, at feet height)
     * from {@code from} to the current objective — the lever of the first closed gate, or the
     * chest once every gate is open. Closed gates block the way. Returns an empty list if
     * {@code from} is not inside the maze proper.
     */
    public List<BlockPos> threadPath(BlockPos from, IntPredicate gateOpen) {
        MazeLayout layout = layout();
        int span = layout.span();
        int sx = from.getX() - originX(), sz = from.getZ() - originZ();
        if (!layout.isInside(sx, sz)) return List.of();

        boolean[] blocked = new boolean[span * span];
        int target = -1;
        for (int g = 0; g < layout.gates().size(); g++) {
            if (gateOpen.test(g)) continue;
            MazeLayout.Gate gate = layout.gates().get(g);
            for (int x = gate.x0(); x <= gate.x1(); x++)
                for (int z = gate.z0(); z <= gate.z1(); z++) blocked[x * span + z] = true;
            if (target < 0) {
                MazeLayout.Lever lever = layout.levers().get(g);
                target = lever.x() * span + lever.z();
            }
        }
        if (target < 0) target = layout.center() * span + layout.center();

        int start = sx * span + sz;
        if (layout.isWall(sx, sz)) return List.of();
        int[] prev = new int[span * span];
        java.util.Arrays.fill(prev, -2);
        prev[start] = -1;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == target) break;
            int cx = cur / span, cz = cur % span;
            for (int[] d : dirs) {
                int nx = cx + d[0], nz = cz + d[1];
                if (!layout.isInside(nx, nz) || layout.isWall(nx, nz)) continue;
                int ni = nx * span + nz;
                if (blocked[ni] || prev[ni] != -2) continue;
                prev[ni] = cur;
                queue.add(ni);
            }
        }
        if (prev[target] == -2) return List.of();
        List<BlockPos> path = new ArrayList<>();
        for (int c = target; c != -1; c = prev[c]) {
            path.add(new BlockPos(originX() + c / span, floorY + 1, originZ() + c % span));
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /** Centre of the maze, at feet height (for the thread's direction outside the maze). */
    public BlockPos centerPos() {
        return chestPos();
    }

    /** Progress (0 = first zone, 1 = last zone) of the corridor at (x, z). */
    public double zoneProgress(int x, int z) {
        int lx = x - originX(), lz = z - originZ();
        int zone = layout().componentOf(Math.floorDiv(lx, MazeLayout.CELL), Math.floorDiv(lz, MazeLayout.CELL));
        int zones = layout().zoneCount();
        if (zone < 0) return 1.0;
        return zones <= 1 ? 1.0 : (double) zone / (zones - 1);
    }

    /** True if (x, z) is a corridor block of the maze (not a wall, not the plaza, not the ring). */
    public boolean isCorridor(int x, int z) {
        int lx = x - originX(), lz = z - originZ();
        return layout().isInside(lx, lz) && !layout().isWall(lx, lz) && !layout().isPlaza(lx, lz);
    }

    /** True if (x, z) is inside the maze proper (not the margin). */
    public boolean isInsideMaze(int x, int z) {
        return layout().isInside(x - originX(), z - originZ());
    }

    /** Loot table of the central chest for this maze size. */
    public static RegistryKey<LootTable> lootTableFor(MazeStyle style, MazeSize size) {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, MazeCraft.id("chests/" + style.lootPrefix() + "_" + size.id()));
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        build(world, chunkBox, random, gate -> false, true, false);
    }

    /**
     * Rebuilds the maze inside one loaded chunk, removing anything that neighbouring chunks'
     * decorations put in it after it was generated (basalt columns, lava deltas, tree leaves...).
     * Opened gates stay open, the chest is left untouched, snow layers are kept.
     */
    public void repair(StructureWorldAccess world, ChunkPos chunk, IntPredicate gateOpen) {
        BlockBox chunkBox = new BlockBox(chunk.getStartX(), world.getBottomY(), chunk.getStartZ(),
                chunk.getEndX(), world.getTopY() - 1, chunk.getEndZ());
        build(world, chunkBox, Random.create(seed ^ chunk.toLong()), gateOpen, false, true);
    }

    private void build(StructureWorldAccess world, BlockBox chunkBox, Random random,
                       IntPredicate gateOpen, boolean placeChest, boolean repairing) {
        MazeLayout layout = layout();
        BlockBox box = this.boundingBox;

        int x0 = Math.max(box.getMinX(), chunkBox.getMinX());
        int x1 = Math.min(box.getMaxX(), chunkBox.getMaxX());
        int z0 = Math.max(box.getMinZ(), chunkBox.getMinZ());
        int z1 = Math.min(box.getMaxZ(), chunkBox.getMaxZ());
        if (x0 > x1 || z0 > z1) return;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        // When repairing, the chest (and its loot) must not be touched
        BlockPos keep = repairing ? chestPos() : null;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int lx = x - originX();
                int lz = z - originZ();
                boolean inside = layout.isInside(lx, lz);
                boolean isWall = inside && layout.isWall(lx, lz);
                boolean isPillar = inside && layout.isPillar(lx, lz);
                boolean isPlaza = inside && layout.isPlaza(lx, lz);

                // 1. Foundation. Enclosed (Nether): a clean underside slab + fortress-like support
                //    pillars down to the ground or into the lava. Overworld: fill gaps until solid ground.
                if (style.enclosed) {
                    buildUnderside(world, pos, x, z);
                }
                for (int y = floorY - 1; !style.enclosed && y >= floorY - FOUNDATION_DEPTH; y--) {
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
                    // Ring: varied mix of non-soil blocks; the approach to the entrance stays plain,
                    // like a path leading to the door
                    floor = layout.isApproach(lx, lz) ? style.floor : style.ring.pick(x, z);
                } else if (isWall) {
                    floor = style.wallBase;
                } else {
                    floor = isPlaza ? style.plaza : style.floor;
                }
                world.setBlockState(pos.set(x, floorY, z), floor, Block.NOTIFY_LISTENERS);

                // 3. Walls, pillars, and clearing above
                if (style.enclosed) {
                    buildEnclosedColumn(world, pos, x, z, lx, lz, inside, isWall, isPillar, air, keep);
                    continue;
                }
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    pos.set(x, floorY + dy, z);
                    BlockState target;
                    if (isWall && dy <= WALL_HEIGHT) {
                        target = isPillar ? style.pillar : style.wallAt(dy);
                    } else if (isPillar && dy == WALL_HEIGHT + 1 && lx % 8 == 0 && lz % 8 == 0) {
                        target = style.light;
                    } else {
                        target = air;
                    }
                    if (pos.equals(keep)) continue;
                    BlockState current = world.getBlockState(pos);
                    if (current != target && !(repairing && current.isOf(Blocks.SNOW) && target.isAir())) {
                        world.setBlockState(pos, target, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 4. Gates (closed) — bars connected along the opening so nobody squeezes through
        for (int gateIndex = 0; gateIndex < layout.gates().size(); gateIndex++) {
            MazeLayout.Gate gate = layout.gates().get(gateIndex);
            if (gateOpen.test(gateIndex)) continue; // opened for good: leave the passage clear
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
        // 6b. Guardians (generation only, never on repair)
        if (!repairing && MazeCraftConfig.get().enableGuardians) {
            spawnGuardians(world, chunkBox);
        }

        BlockPos chest = chestPos();
        if (placeChest && chunkBox.contains(chest)) {
            world.setBlockState(chest,
                    Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            BlockEntity be = world.getBlockEntity(chest);
            if (be instanceof ChestBlockEntity chestEntity) {
                chestEntity.setLootTable(lootTableFor(style, size), random.nextLong());
            }
        }
    }

    /** Spacing of the support pillars under enclosed mazes (2×2 pillars). */
    private static final int SUPPORT_SPACING = 12;
    /** Maximum pillar length (blocks below the underside slab). */
    private static final int SUPPORT_MAX_DEPTH = 80;

    /** True on a 2-wide band every SUPPORT_SPACING blocks, and along the far edge. */
    private static boolean supportBand(int v, int side) {
        return Math.floorMod(v, SUPPORT_SPACING) < 2 || v >= side - 2;
    }

    /**
     * Enclosed mazes: underside slab (styled, replaces the old netherrack blob) and, on a grid,
     * 2×2 support pillars going down through air and lava until they reach solid ground.
     */
    private void buildUnderside(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z) {
        BlockBox box = this.boundingBox;
        pos.set(x, floorY - 1, z);
        if (world.getBlockState(pos) != style.ceiling) {
            world.setBlockState(pos, style.ceiling, Block.NOTIFY_LISTENERS);
        }
        int side = box.getMaxX() - box.getMinX() + 1;
        if (!supportBand(x - box.getMinX(), side) || !supportBand(z - box.getMinZ(), side)) return;
        int bottom = Math.max(world.getBottomY() + 1, floorY - 1 - SUPPORT_MAX_DEPTH);
        for (int y = floorY - 2; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState current = world.getBlockState(pos);
            boolean solid = !current.isAir() && !current.isReplaceable() && current.getFluidState().isEmpty();
            if (solid) break;
            world.setBlockState(pos, style.pillar, Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * One column of an enclosed (Nether) maze: walls up to the roof, a sealed outer wall around
     * the whole box (except the doorway in front of the entrance), and a roof with lights.
     */
    private void buildEnclosedColumn(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, int lx, int lz,
                                     boolean inside, boolean isWall, boolean isPillar, BlockState air, BlockPos keep) {
        BlockBox box = this.boundingBox;
        boolean perimeter = x == box.getMinX() || x == box.getMaxX() || z == box.getMinZ() || z == box.getMaxZ();
        boolean doorway = perimeter && layout().isApproach(lx, lz);
        // Light spots: centre of every other cell, inside the maze and in the ring
        int mx = Math.floorMod(lx, MazeLayout.CELL * 2), mz = Math.floorMod(lz, MazeLayout.CELL * 2);
        boolean lightSpot = mx == 2 && mz == 2 && !isWall;

        for (int dy = 1; dy <= ROOF_DY; dy++) {
            pos.set(x, floorY + dy, z);
            BlockState target;
            if (dy == ROOF_DY) {
                // Cornice: the roof edge is outlined with the pillar block
                target = perimeter ? style.pillar
                        : lightSpot && style.hangingLight == null ? style.ceilingLight : style.ceiling;
            } else if (perimeter && !doorway) {
                // Plinth: bottom course of the outer wall in the pillar block
                target = dy == 1 ? style.pillar : style.wallAt(dy);
            } else if (isWall) {
                target = isPillar ? style.pillar : style.wallAt(dy);
            } else if (lightSpot && style.hangingLight != null && dy == WALL_HEIGHT) {
                target = style.hangingLight;
            } else {
                target = air;
            }
            if (pos.equals(keep)) continue;
            if (world.getBlockState(pos) != target) {
                world.setBlockState(pos, target, Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * Guardians: 2–4 per zone (x config multiplier), dead ends first, in the centre of their
     * cell. Deeper zones get better armor. Only the ones whose cell is in this chunk spawn,
     * so each guardian is spawned exactly once.
     */
    private void spawnGuardians(StructureWorldAccess world, BlockBox chunkBox) {
        MazeLayout layout = layout();
        double mult = MazeCraftConfig.get().enemyMultiplier;
        int min = Math.max(0, (int) Math.round(2 * mult));
        int max = Math.max(min, (int) Math.round(4 * mult));
        if (max == 0) return;
        java.util.Random rng = new java.util.Random(seed ^ 0x6A09E667F3BCC909L);
        List<int[]> cells = layout.guardianCells(rng, min, max);
        List<EntityType<? extends MobEntity>> pool = MazeEnemies.pool(style);
        int zones = Math.max(1, layout.zoneCount());
        for (int[] cell : cells) {
            BlockPos pos = new BlockPos(originX() + MazeLayout.CELL * cell[0] + 2, floorY + 1,
                    originZ() + MazeLayout.CELL * cell[1] + 2);
            if (!chunkBox.contains(pos)) continue;
            double progress = zones <= 1 ? 1.0 : (double) cell[2] / (zones - 1);
            int tier = size.enemyTier(progress); // scales with the maze size (small: none → leather)
            EntityType<? extends MobEntity> type = pool.get(rng.nextInt(pool.size()));
            MazeEnemies.spawn(world, type, pos, tier, true, SpawnReason.STRUCTURE);
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
