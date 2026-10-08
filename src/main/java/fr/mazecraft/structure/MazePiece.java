package fr.mazecraft.structure;

import fr.mazecraft.block.CodeKeyBlock;
import fr.mazecraft.block.ModBlocks;
import fr.mazecraft.block.SealedGatewayBlock;
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
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
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

    /**
     * Half-width of the central room, in cells. Kronos gets 2 — a 19 × 19 arena instead of the
     * ordinary 11 × 11 chest room — because its Minotaur charges in a straight line and is
     * stunned when it hits a wall: in an 11-block room there is no run-up and the fight is a
     * scrum in a corner.
     */
    public int plazaRadius() {
        return style.isKronos() ? 2 : 1;
    }

    /** Radius, in blocks, of the sunken fighting floor of the arena of Kronos. */
    private static final int ARENA_FLOOR = 6;
    /** How far the fighting floor sits below the rest of the maze. */
    private static final int ARENA_DEPTH = 2;

    /**
     * How far below the maze floor this column sits: the arena of Kronos is dug out of the
     * plaza, a 13 × 13 fighting floor two blocks down, reached by a ring of two steps.
     *
     * <p>Sunken rather than flat so the fight can be watched from the rim, and so the plaza
     * door opens onto a view of the arena instead of into it. The two extra blocks of headroom
     * are for a boss that is taller than a corridor.</p>
     *
     * @return 0 outside the arena, 1 on the upper step, {@link #ARENA_DEPTH} on the floor
     */
    private int arenaSink(int lx, int lz) {
        if (!style.isKronos() || !layout().isPlaza(lx, lz)) return 0;
        int centre = size.span() / 2;
        int d = Math.max(Math.abs(lx - centre), Math.abs(lz - centre));
        if (d <= ARENA_FLOOR) return ARENA_DEPTH;
        return d == ARENA_FLOOR + 1 ? 1 : 0;
    }

    /** Half-width of the hoard chamber: 15 × 15 outside, 13 × 13 of room. */
    private static final int HOARD_RADIUS = 7;
    /** Depth of the chamber floor below the maze floor, and of its ceiling. */
    private static final int HOARD_FLOOR = 10, HOARD_CEIL = 5;

    /** Centre of the maze in local coordinates. */
    private int localCentre() {
        return size.span() / 2;
    }

    // =====================================================================
    // The vault of a colossal maze — the keypad room
    // =====================================================================

    /**
     * Cells the vault's dead end keeps from the maze's outer wall.
     *
     * <p>Three cells is twelve blocks, and the room reaches ten blocks from the middle of its
     * cell at the furthest — so the whole of it, walls included, stays inside the footprint the
     * maze protects and the repair pass rebuilds.</p>
     */
    private static final int VAULT_CLEARANCE = 3;

    /** Depth of the vault's floor and of its ceiling below the maze floor. */
    private static final int VAULT_FLOOR = 10, VAULT_CEIL = 5;

    /**
     * The room, as block offsets from the middle of its dead-end cell. Two chambers end to end:
     * the antechamber you come down into, with the twelve keys on its far wall, and the treasure
     * room behind the sealed door.
     *
     * <pre>
     *   dz = -5   keypad wall — twelve keys at dx -6..+5
     *   dz -4..3  antechamber (8 deep), the stair shaft at its middle
     *   dz =  4   the sealed door, three wide at dx -1..+1
     *   dz  5..9  treasure room (5 deep), the chest against its far wall
     *   dz = 10   back wall
     *   dx = -8 and +7 side walls; dx -7..+6 interior (14 wide)
     * </pre>
     *
     * <p>Fixed in world space rather than turned to face the corridor: the stair comes down the
     * middle of the antechamber whichever way the dead end runs, so the room reads the same from
     * the bottom step either way, and a room that never rotates is a room whose every block is a
     * pure function of its coordinates — which is what lets generation and the repair pass agree
     * on it without storing anything.</p>
     */
    private static final int VAULT_X0 = -8, VAULT_X1 = 7;
    private static final int VAULT_Z0 = -5, VAULT_Z1 = 10;
    /** The wall the sealed door stands in, and the half-width of the doorway. */
    private static final int VAULT_DOOR_Z = 4, VAULT_DOOR_HALF = 1;
    /** The leftmost of the twelve keys. */
    private static final int VAULT_KEY_X0 = -6;
    /** Height of the keys above the vault floor — eye level for somebody standing on it. */
    private static final int VAULT_KEY_DY = 2;

    /** Stands in the cache for "asked, and this maze has no vault" — see {@link #vaultCell()}. */
    private static final int[] NO_VAULT = new int[0];

    /** Cached: {cx, cz} of the vault's dead end, {@link #NO_VAULT}, or null if not asked yet. */
    private int[] vaultCell;

    /** True for a maze that has a vault at all: the colossal ones, Kronos excepted. */
    public boolean hasVault() {
        return size == MazeSize.COLOSSAL && !style.isKronos();
    }

    /**
     * The dead end the vault hangs under, or null.
     *
     * <p>Cached, and that is not an optimisation: choosing the cell walks the whole maze, and
     * {@link #build} asks for it once per column — fifty thousand times for a colossal maze.</p>
     *
     * <p>One field and one write, with {@link #NO_VAULT} standing in for "there is none", rather
     * than a value and a {@code known} flag. Chunks of the same maze generate on several threads
     * at once: with two fields, a thread can see the flag already set and the value not yet
     * written, and build a chunk of maze with the room missing out of it.</p>
     */
    private int[] vaultCell() {
        int[] cached = vaultCell;
        if (cached == null) {
            int[] found = hasVault() ? layout().vaultDeadEnd(VAULT_CLEARANCE) : null;
            cached = found == null ? NO_VAULT : found;
            vaultCell = cached;
        }
        return cached == NO_VAULT ? null : cached;
    }

    /** Local X/Z of the middle of the vault's dead-end cell, or null. */
    private int[] vaultOrigin() {
        int[] cell = vaultCell();
        return cell == null ? null
                : new int[]{MazeLayout.CELL * cell[0] + 2, MazeLayout.CELL * cell[1] + 2};
    }

    /** World position of the top of the vault's stair — the dead end, at feet height. */
    public BlockPos vaultEntrance() {
        int[] o = vaultOrigin();
        return o == null ? null : new BlockPos(originX() + o[0], floorY + 1, originZ() + o[1]);
    }

    /** Somewhere to stand in the antechamber, in front of the keys (debug teleport). */
    public BlockPos vaultFloorPos() {
        int[] o = vaultOrigin();
        return o == null ? null
                : new BlockPos(originX() + o[0], floorY - VAULT_FLOOR + 1, originZ() + o[1] - 3);
    }

    /**
     * The four digits that open this vault.
     *
     * <p>Derived from the maze's seed and nothing else, so the code is the same every time the
     * piece is rebuilt, the same for every player, the same after a restart, and the same for
     * the plaques as for the door — without a byte of it being saved anywhere. It cannot be
     * read off the world either: a player holding the seed could compute it, which is the same
     * bargain every seeded secret in Minecraft makes.</p>
     *
     * <p>A splitmix64 finalizer rather than {@link #hash}, which is good enough for scattering
     * gold over a floor and not for this. Measured over 200 000 seeds: the position-hash gave
     * only <b>1 765 of the 10 000 possible codes</b>, its commonest code came up 28 times more
     * often than it should, and 1234-style runs appeared 24 times more often than chance —
     * because consecutive digits differed by one multiply in the mix and stayed correlated.
     * With a real mixer all 10 000 codes occur, and every pattern lands where chance puts
     * it.</p>
     */
    public int[] vaultCode() {
        int[] code = new int[CodeKeyBlock.SLOTS];
        for (int i = 0; i < code.length; i++) {
            long h = seed * 0x9E3779B97F4A7C15L + (i + 1) * 0xBF58476D1CE4E5B9L;
            h ^= h >>> 30;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 27;
            h *= 0x94D049BB133111EBL;
            h ^= h >>> 31;
            code[i] = (int) Math.floorMod(h, 10L);
        }
        return code;
    }

    /** Cached dead ends carrying the plaques, one per zone; see {@link #codePlaqueCells()}. */
    private int[][] plaqueCells;

    /**
     * The dead ends carrying the code's four digits, indexed by slot. An entry may be null
     * when a zone had no dead end to spare.
     */
    private int[][] codePlaqueCells() {
        int[][] cached = plaqueCells;
        if (cached == null) {
            cached = hasVault()
                    ? layout().codePlaqueCells(1, CodeKeyBlock.SLOTS, vaultCell())
                    : new int[CodeKeyBlock.SLOTS][];
            if (cached == null) cached = new int[CodeKeyBlock.SLOTS][];
            plaqueCells = cached;
        }
        return cached;
    }

    /** World position of the digit plaque for {@code slot} (0-based), or null. */
    public BlockPos codePlaquePos(int slot) {
        int[][] cells = codePlaqueCells();
        if (slot < 0 || slot >= cells.length || cells[slot] == null) return null;
        int[] wall = plaqueWall(cells[slot]);
        return wall == null ? null
                : new BlockPos(originX() + wall[0], floorY + PLAQUE_DY, originZ() + wall[1]);
    }

    /** Height of the digit plaque above the corridor floor — eye level for a standing player. */
    private static final int PLAQUE_DY = 2;

    /**
     * The wall block a plaque hangs on: the middle of the wall <b>facing the way in</b>, so it
     * is the first thing seen on entering the dead end and cannot be missed by walking in and
     * straight back out.
     *
     * @return {@code {localX, localZ, facing}} of the wall block, or null
     */
    private int[] plaqueWall(int[] cell) {
        int exit = layout().onlyExit(cell[0], cell[1]);
        if (exit < 0) return null;
        // The wall opposite the doorway, and the direction its face looks (back at the player).
        int opposite = switch (exit) {
            case MazeLayout.NORTH -> MazeLayout.SOUTH;
            case MazeLayout.SOUTH -> MazeLayout.NORTH;
            case MazeLayout.WEST -> MazeLayout.EAST;
            default -> MazeLayout.WEST;
        };
        int[] mid = layout().wallMiddleOf(cell[0], cell[1], opposite);
        return new int[]{mid[0], mid[1], exit};
    }

    /** Which way a plaque's face looks: back down the corridor the player came from. */
    private Direction plaqueFacing(int[] cell) {
        int[] wall = plaqueWall(cell);
        if (wall == null) return Direction.NORTH;
        return switch (wall[2]) {
            case MazeLayout.NORTH -> Direction.NORTH;
            case MazeLayout.SOUTH -> Direction.SOUTH;
            case MazeLayout.WEST -> Direction.WEST;
            default -> Direction.EAST;
        };
    }

    /**
     * Writes the four plaques. Called after the walls are raised, like the levers, because the
     * wall loop would otherwise write straight over them.
     */
    private void buildPlaques(StructureWorldAccess world, BlockBox chunkBox) {
        if (!hasVault()) return;
        int[] code = vaultCode();
        int[][] cells = codePlaqueCells();
        for (int slot = 0; slot < cells.length; slot++) {
            int[] cell = cells[slot];
            if (cell == null) continue;
            int[] wall = plaqueWall(cell);
            if (wall == null) continue;
            Direction facing = plaqueFacing(cell);
            int wx = originX() + wall[0], wz = originZ() + wall[1];

            BlockPos digit = new BlockPos(wx, floorY + PLAQUE_DY, wz);
            if (chunkBox.contains(digit)) {
                world.setBlockState(digit, ModBlocks.CODE_KEY.getDefaultState()
                        .with(CodeKeyBlock.KEY, code[slot])
                        .with(CodeKeyBlock.FACING, facing), Block.NOTIFY_LISTENERS);
            }
            BlockPos tally = digit.up();
            if (chunkBox.contains(tally)) {
                world.setBlockState(tally, ModBlocks.CODE_KEY.getDefaultState()
                        .with(CodeKeyBlock.KEY, CodeKeyBlock.SLOT + slot)
                        .with(CodeKeyBlock.FACING, facing), Block.NOTIFY_LISTENERS);
            }
        }
    }

    /** World position of key {@code key} (0–9, {@link CodeKeyBlock#RESET}, {@link CodeKeyBlock#ENTER}). */
    public BlockPos codeKeyPos(int key) {
        int[] o = vaultOrigin();
        return o == null ? null : new BlockPos(originX() + o[0] + VAULT_KEY_X0 + key,
                floorY - VAULT_FLOOR + VAULT_KEY_DY, originZ() + o[1] + VAULT_Z0);
    }

    /** The nine blocks of the sealed door, or an empty list. */
    public List<BlockPos> vaultDoorBlocks() {
        List<BlockPos> list = new ArrayList<>();
        int[] o = vaultOrigin();
        if (o == null) return list;
        for (int dx = -VAULT_DOOR_HALF; dx <= VAULT_DOOR_HALF; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                list.add(new BlockPos(originX() + o[0] + dx, floorY - VAULT_FLOOR + dy,
                        originZ() + o[1] + VAULT_DOOR_Z));
            }
        }
        return list;
    }

    /**
     * Is the vault's door already down?
     *
     * <p>Asks the door rather than a record of the door. {@link fr.mazecraft.protection.MazeState}
     * remembers which vaults have been opened, and for a naturally generated maze that is the
     * same answer — but it is keyed on the structure, so a maze from {@code /maze debug place}
     * has no entry at all and the keypad happily reopened an open vault, replaying the message
     * and the sound every time the code was typed again. It would also disagree with the world
     * if the blocks were ever taken out by hand.</p>
     *
     * <p>The same "compute, don't store" rule the shifting walls, the arena door and the hoard
     * all follow: the blocks are the truth, and one of them is enough to read it.</p>
     */
    public boolean isVaultDoorOpen(net.minecraft.world.BlockView world) {
        List<BlockPos> door = vaultDoorBlocks();
        return !door.isEmpty() && !world.getBlockState(door.get(0)).isOf(ModBlocks.VAULT_DOOR);
    }

    /** The chest of the treasure room, or null. */
    public BlockPos vaultChestPos() {
        int[] o = vaultOrigin();
        return o == null ? null : new BlockPos(originX() + o[0],
                floorY - VAULT_FLOOR + 2, originZ() + o[1] + VAULT_Z1 - 2);
    }

    /**
     * Is this block part of the vault — its rooms, its walls, its shaft?
     *
     * <p>What this guards is the one way the puzzle could be walked around. Everything else in a
     * maze stops being protected the moment the maze is conquered, and that is deliberate: it is
     * yours afterwards. But the vault outlives the conquest, so without this the answer to a
     * four-digit code would be a pickaxe and nine blocks of wall.</p>
     */
    public boolean isInVault(BlockPos pos) {
        int[] o = vaultOrigin();
        if (o == null) return false;
        int dx = pos.getX() - (originX() + o[0]);
        int dz = pos.getZ() - (originZ() + o[1]);
        int y = pos.getY();
        // The shaft, from the dead-end floor down to the ceiling.
        if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && y <= floorY && y >= floorY - VAULT_CEIL) {
            return true;
        }
        return dx >= VAULT_X0 && dx <= VAULT_X1 && dz >= VAULT_Z0 && dz <= VAULT_Z1
                && y >= floorY - VAULT_FLOOR && y <= floorY - VAULT_CEIL;
    }

    /** The double chest of the hoard: against the far wall, on its pedestal. */
    public BlockPos hoardChestPos() {
        int c = localCentre();
        return new BlockPos(originX() + c, floorY - HOARD_FLOOR + 2, originZ() + c + HOARD_RADIUS - 2);
    }

    public MazeLayout layout() {
        if (layout == null) {
            layout = MazeLayout.generate(size.cells(), seed, entranceSide, size.gates(),
                    plazaRadius());
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
        return entranceOutsidePos(2);
    }

    /**
     * World position (feet) on the approach line, {@code distance} blocks out from the maze.
     * Past {@code style.margin} this leaves the bounding box altogether — which is what anything
     * built out there has to do, because generation and the repair pass both rewrite every
     * column inside the box, roof included, and would bury it.
     */
    public BlockPos entranceOutsidePos(int distance) {
        int[] l = layout().entranceOutside(distance);
        return new BlockPos(originX() + l[0], floorY + 1, originZ() + l[1]);
    }

    /** Flattened ring around the maze, in blocks; part of the bounding box. */
    public int getMargin() {
        return style.margin;
    }

    /**
     * World positions of the sealed gateway plane filling the entrance doorway
     * (3 wide × WALL_HEIGHT high). Empty for sizes that are never sealed.
     */
    public List<BlockPos> entranceBlocks() {
        List<BlockPos> list = new ArrayList<>();
        for (int[] cell : layout().entranceGap()) {
            for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                list.add(new BlockPos(originX() + cell[0], floorY + dy, originZ() + cell[1]));
            }
        }
        return list;
    }

    /** Logs and leaves: left in place over the ring so overhanging trees stay whole. */
    private static boolean isTreeMatter(BlockState state) {
        return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
    }

    /** True when the entrance doorway runs along the X axis. */
    public boolean entranceAlongX() {
        return layout().entranceAlongX();
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

    /**
     * How many wall segments of a Kronos vault shift.
     *
     * <p>Measured rather than guessed. A colossal maze is 3025 cells and the walk from the
     * entrance to the plaza is about 200 of them: at forty segments only 2.9 of them touched
     * that walk, so most players would cross the whole vault without watching a single wall
     * move. At 160 it is 11.3 — one roughly every seventy blocks of corridor — while still
     * leaving around 880 eligible walls to draw from, so the choice stays varied and every
     * segment still clears the detour bar.</p>
     */
    public static final int MOVABLE_WALLS = 160;

    private List<MazeLayout.Gate> movable;

    /** The wall segments that open and close while the maze is walked (Kronos only). */
    public List<MazeLayout.Gate> movableWalls() {
        if (movable == null) {
            movable = style.isKronos()
                    ? layout().movableWalls(seed ^ 0x51F7L, MOVABLE_WALLS)
                    : List.of();
        }
        return movable;
    }

    /**
     * One block of movable wall {@code index}, to test distance against without building the
     * whole segment. The tick looks at every segment of the vault several times a second and
     * all but a handful are too far away to matter — those must cost one allocation, not
     * fifteen.
     */
    public BlockPos movableWallAnchor(int index) {
        MazeLayout.Gate seg = movableWalls().get(index);
        return new BlockPos(originX() + seg.x0(), floorY + 1, originZ() + seg.z0());
    }

    /** World positions of movable wall {@code index} (3 wide × WALL_HEIGHT high). */
    public List<BlockPos> movableWallBlocks(int index) {
        List<BlockPos> list = new ArrayList<>();
        MazeLayout.Gate seg = movableWalls().get(index);
        for (int x = seg.x0(); x <= seg.x1(); x++) {
            for (int z = seg.z0(); z <= seg.z1(); z++) {
                for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                    list.add(new BlockPos(originX() + x, floorY + dy, originZ() + z));
                }
            }
        }
        return list;
    }

    /**
     * The block a shifting wall is made of: chiselled deepslate.
     *
     * <p>Three answers were tried here. The pillar block was wrong twice over — it <em>is</em>
     * what the structural pillars are made of, so a shifting wall could not be told from a
     * corner, and a hundred and sixty bronze segments turned a deepslate tomb into a copper
     * mine. The ordinary wall block, with a bronze rail let into the floor, hid the mechanism
     * well but drew a cross on the ground at every one of them.</p>
     *
     * <p>So: the tell is the wall itself, in a stone of the same family. At a glance down a
     * corridor it is one more dark wall; looked at, the chiselled face is not the polished one.
     * That is the line between a mechanism a player can learn and one that is merely random —
     * without any tell, a corridor that was shut and is now open reads as a faulty memory, and
     * a wall closed behind you cannot be told from a dead end.</p>
     */
    public BlockState movableWallState() {
        return Blocks.CHISELED_DEEPSLATE.getDefaultState();
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

    /** Is this world position inside the central room (the arena, for Kronos)? */
    public boolean isInPlaza(BlockPos pos) {
        return layout().isPlaza(pos.getX() - originX(), pos.getZ() - originZ());
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
        return threadPath(from, gateOpen, null);
    }

    /**
     * The same, reading the world so that the shifting walls of Kronos count.
     *
     * <p>Without the world the thread only knows the layout, which says every movable segment is
     * a wall — so it routed the player the long way round past a shortcut that was standing wide
     * open in front of them. With it, a segment that is open right now is just a passage.</p>
     *
     * <p>The path is worked out afresh on every use, so a shortcut that shuts again is not a
     * trap: the next use simply routes around it. It cannot strand anyone either — the maze is
     * a tree and these segments only ever add loops, so the long way round never stops
     * existing.</p>
     */
    public List<BlockPos> threadPath(BlockPos from, IntPredicate gateOpen,
                                     net.minecraft.world.BlockView world) {
        MazeLayout layout = layout();
        int span = layout.span();
        int sx = from.getX() - originX(), sz = from.getZ() - originZ();
        if (!layout.isInside(sx, sz)) return List.of();

        // Segments of shifting wall that are standing open right now: passable, whatever the
        // layout says.
        boolean[] opened = new boolean[span * span];
        if (world != null && style.isKronos()) {
            for (int i = 0; i < movableWalls().size(); i++) {
                List<BlockPos> blocks = movableWallBlocks(i);
                if (blocks.isEmpty() || !world.getBlockState(blocks.get(0)).isAir()) continue;
                MazeLayout.Gate seg = movableWalls().get(i);
                for (int x = seg.x0(); x <= seg.x1(); x++) {
                    for (int z = seg.z0(); z <= seg.z1(); z++) opened[x * span + z] = true;
                }
            }
        }

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
                if (!layout.isInside(nx, nz)) continue;
                int ni = nx * span + nz;
                if (layout.isWall(nx, nz) && !opened[ni]) continue;
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

                // 2b. The arena of Kronos is dug down out of the plaza: the floor just written
                //     becomes air and is laid again lower. Written this way round so the
                //     ordinary path is untouched and only Kronos pays for it.
                int sink = arenaSink(lx, lz);
                if (sink > 0) {
                    for (int d = 0; d < sink; d++) {
                        world.setBlockState(pos.set(x, floorY - d, z), air, Block.NOTIFY_LISTENERS);
                    }
                    world.setBlockState(pos.set(x, floorY - sink, z), style.plaza, Block.NOTIFY_LISTENERS);
                    // Nothing must be left hanging under the dug-out floor.
                    world.setBlockState(pos.set(x, floorY - sink - 1, z), style.foundation,
                            Block.NOTIFY_LISTENERS);
                }

                // 2c. The hoard of Asterion, sealed under the arena. Built with the vault, not
                //     when he dies: a room this size cannot be raised block by block from a
                //     tick without the player watching it appear. What his death does is open
                //     the way down to it.
                if (style.isKronos()) buildHoard(world, pos, x, z, lx, lz, placeChest);

                // 2d. The vault of a colossal maze: the keypad room, under the deepest dead end.
                //     Built with the maze and left open — finding the stair is what tells you
                //     there is a code to look for. Only the door behind the keys is sealed.
                if (hasVault()) buildVault(world, pos, x, z, lx, lz, placeChest, repairing);

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
                    // Over the ring, leave trees alone. Nothing can root in the ring (its floor is
                    // never a soil block) and the maze proper is further from any possible trunk
                    // than a canopy can reach — so every tree sliced flat along the edge was cut
                    // by THIS clearing, not grown into the maze. Skipping tree matter here keeps
                    // overhanging branches whole instead of leaving half a tree, and costs
                    // nothing: it is outside the walls.
                    if (!inside && isTreeMatter(current)) continue;
                    if (current != target && !(repairing && current.isOf(Blocks.SNOW) && target.isAir())) {
                        world.setBlockState(pos, target, Block.NOTIFY_LISTENERS);
                    }
                }

                // 3b. Everything still standing above the cleared height, up to the build limit.
                clearAbove(world, pos, x, z, inside);
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

        // 4b. Sealed gateway across the entrance (medium and large mazes only). It is only the
        // visible signal — who may walk through is decided per player in MazeBarrier.
        SealedGatewayBlock.Tier tier = SealedGatewayBlock.Tier.of(style, size);
        if (tier != null) {
            BlockState gateway = ModBlocks.SEALED_GATEWAY.getDefaultState()
                    .with(SealedGatewayBlock.AXIS, entranceAlongX() ? Direction.Axis.X : Direction.Axis.Z)
                    .with(SealedGatewayBlock.TIER, tier);
            for (BlockPos gatewayPos : entranceBlocks()) {
                if (chunkBox.contains(gatewayPos)) {
                    world.setBlockState(gatewayPos, gateway, Block.NOTIFY_LISTENERS);
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

        // 5b. The four plaques carrying the vault's code, one per zone. After the walls, like
        //     the levers: the wall loop writes every block of its own column and would bury them.
        buildPlaques(world, chunkBox);

        // 6. Central chest
        // 6b. Guardians (generation only, never on repair)
        if (!repairing && MazeCraftConfig.get().enableGuardians) {
            spawnGuardians(world, chunkBox);
        }

        // The arena of Kronos has no chest at its centre: what is at the centre is the
        // Minotaur, and the hoard is only opened once it is down.
        BlockPos chest = chestPos();
        if (placeChest && !style.isKronos() && chunkBox.contains(chest)) {
            world.setBlockState(chest,
                    Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            BlockEntity be = world.getBlockEntity(chest);
            if (be instanceof ChestBlockEntity chestEntity) {
                chestEntity.setLootTable(lootTableFor(style, size), random.nextLong());
            }
        }
    }

    /**
     * Clears what is left hanging over the maze, from {@link #CLEAR_HEIGHT} to the build limit.
     *
     * <p>The maze clears ten blocks of headroom and stops. That is fine on flat ground and
     * wrong everywhere else: a hillside, an overhang or a cliff that reaches higher than ten
     * blocks keeps its top while the ground beneath it is cut away, and what is left hangs in
     * the air over the corridors. The ten blocks were never a design decision about how much
     * sky a maze gets — they are just how far the old loop ran.</p>
     *
     * <p>So the column is taken all the way up. The cut is square-edged where a mountain meets
     * the maze's boundary, which is the honest trade: a vertical face reads as something that
     * was carved, floating ground reads as a broken mod.</p>
     *
     * <p>Over the margin ring, a tree is left alone <b>until the column has had ground taken
     * out from under it</b>. That one condition settles both ways of getting it wrong: a canopy
     * overhanging the ring from a trunk rooted outside the maze is still spared, as it is below
     * the clearing height, but a tree standing on a shelf this pass is about to remove comes
     * down with it instead of being left hanging in the air. Sparing trees unconditionally
     * would trade floating ground for floating trees, which is not a trade.</p>
     *
     * <p>Cost is a read per block of sky and a write only where something is actually in the
     * way — roughly sixty thousand reads per chunk, a few milliseconds, and nothing at all to
     * write over the flat ground a maze normally lands on.</p>
     */
    private void clearAbove(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                            boolean inside) {
        // Enclosed mazes are buried in rock under a roof of their own: there is no sky over
        // them to clear, and taking the column up would bore a shaft to the Nether ceiling.
        if (style.enclosed) return;

        BlockState air = Blocks.AIR.getDefaultState();
        boolean groundCut = false;
        for (int y = floorY + CLEAR_HEIGHT + 1; y < world.getTopY(); y++) {
            pos.set(x, y, z);
            BlockState current = world.getBlockState(pos);
            if (current.isAir()) continue;
            boolean tree = isTreeMatter(current);
            if (!inside && tree && !groundCut) continue;
            if (!tree) groundCut = true;
            world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
        }
    }

    /**
     * One column of the hoard chamber of Asterion.
     *
     * <p>Minecraft hands out treasure in chests: you walk to a box, open it, and the reward is
     * a list. The point of this room is to be <em>looked at</em> — gold under your feet, bronze
     * on the walls, soul-light on all of it — so that the last thing the labyrinth gives you is
     * a sight and not an inventory screen.</p>
     *
     * <p>What is where is a pure function of the block's coordinates, never of the {@code
     * Random} passed in: generation and the repair pass are seeded differently, so anything
     * drawn from that random would have rearranged itself the first time a chunk was repaired.</p>
     */
    private void buildHoard(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                            int lx, int lz, boolean placeChest) {
        int c = localCentre();
        int dx = lx - c, dz = lz - c;
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d > HOARD_RADIUS) return;

        int floor = floorY - HOARD_FLOOR;
        int ceiling = floorY - HOARD_CEIL;
        BlockState brick = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        BlockState bronze = Blocks.OXIDIZED_COPPER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        boolean wall = d == HOARD_RADIUS;

        // The two blocks the double chest stands on. A repair pass must not write to them at
        // all: it rebuilds the room with placeChest false, so it used to fill the chest's own
        // position with air — which scatters the chest's contents on the floor and leaves no
        // chest — and then never put one back. Every chunk load did it again.
        BlockPos chest = hoardChestPos();
        boolean onChest = z == chest.getZ() && (x == chest.getX() || x == chest.getX() + 1);
        int chestY = floor + 2;

        // Floor, then the room, then the lid.
        pos.set(x, floor, z);
        world.setBlockState(pos, wall ? brick : hoardFloor(dx, dz), Block.NOTIFY_LISTENERS);

        for (int y = floor + 1; y < ceiling; y++) {
            if (onChest && y == chestY && !placeChest) continue;
            pos.set(x, y, z);
            if (wall) {
                // Bronze pilasters every four blocks, brick between, a little of it cracked.
                boolean pilaster = (Math.abs(dx) == HOARD_RADIUS && Math.abs(dz) % 4 == 0)
                        || (Math.abs(dz) == HOARD_RADIUS && Math.abs(dx) % 4 == 0);
                world.setBlockState(pos, pilaster ? bronze
                        : (hash(dx, dz, y) % 5 == 0 ? cracked : brick), Block.NOTIFY_LISTENERS);
            } else {
                // SKIP_DROPS as well as a plain air write: emptying a room should never be able
                // to spill anything on its floor, whatever ends up standing in it one day.
                world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        }
        pos.set(x, ceiling, z);
        world.setBlockState(pos, hash(dx, dz, 0) % 6 == 0 ? cracked : brick, Block.NOTIFY_LISTENERS);

        if (wall) return;

        // Four hanging lights, off the diagonals so they light the chest and the stair.
        if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
            pos.set(x, ceiling - 1, z);
            world.setBlockState(pos, Blocks.SOUL_LANTERN.getDefaultState()
                    .with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
            return;
        }

        // The pedestal, four blocks of it, and the double chest standing on the middle two.
        if (z == chest.getZ() && x >= chest.getX() - 1 && x <= chest.getX() + 2) {
            pos.set(x, floor + 1, z);
            world.setBlockState(pos, Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            if (onChest && placeChest) {
                // Two ADJACENT blocks. They used to be put one on each side of the pedestal's
                // middle, a block apart — which is not a double chest at all, just two single
                // ones that cannot see each other.
                placeHoardChest(world, new BlockPos(x, chestY, z),
                        x == chest.getX() ? ChestType.LEFT : ChestType.RIGHT);
            }
            return;
        }

        // Heaped treasure, standing on the floor: the part you see before you see the chest.
        BlockState heap = hoardHeap(dx, dz);
        if (heap != null) {
            pos.set(x, floor + 1, z);
            world.setBlockState(pos, heap, Block.NOTIFY_LISTENERS);
            if (hash(dx, dz, 7) % 4 == 0) {
                pos.set(x, floor + 2, z);
                world.setBlockState(pos, heap, Block.NOTIFY_LISTENERS);
            }
        }
    }

    /** The eight cells round a 3 × 3 shaft: one step per half block makes a walkable spiral. */
    private static final int[][] SHAFT_SPIRAL = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    /**
     * One column of the vault of a colossal maze.
     *
     * <p>Written exactly like the hoard of Asterion, and for the same reason: a room this size
     * cannot be raised from a tick, and nothing about it may depend on the {@code Random} handed
     * to a build pass, because generation and the repair pass are seeded differently and the room
     * would rearrange itself the first time a chunk was repaired.</p>
     *
     * <p>Two things here are <em>not</em> rebuilt on a repair: the sealed door and the chest. Both
     * are state — a door that has been opened, a chest that has been looted — and a pass that
     * knows nothing about either must leave them exactly as it found them. Putting the door back
     * would re-seal a vault somebody had already solved; writing the chest's own block would
     * scatter its contents on the floor, which is precisely the bug the hoard shipped with.</p>
     */
    private void buildVault(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                            int lx, int lz, boolean placeChest, boolean repairing) {
        int[] o = vaultOrigin();
        if (o == null) return;
        int dx = lx - o[0], dz = lz - o[1];
        if (dx < VAULT_X0 || dx > VAULT_X1 || dz < VAULT_Z0 || dz > VAULT_Z1) return;

        int floor = floorY - VAULT_FLOOR;
        int ceiling = floorY - VAULT_CEIL;
        BlockState shell = style.wallBase;
        BlockState air = Blocks.AIR.getDefaultState();

        boolean wall = dx == VAULT_X0 || dx == VAULT_X1
                || dz == VAULT_Z0 || dz == VAULT_Z1 || dz == VAULT_DOOR_Z;

        // The chest's own block, which a repair pass must not write to AT ALL.
        //
        // Emptying the room writes air over every block of it, the chest included, and the chest
        // is only put back when placeChest is true — which it never is on a repair. So the first
        // time the chunk was repaired the chest was deleted and its contents spilled on the
        // floor. This is the exact bug the hoard of Asterion shipped with; the hoard was guarded
        // and this room, written afterwards from the same pattern, was not.
        BlockPos chest = vaultChestPos();
        boolean onChest = !wall && chest != null && x == chest.getX() && z == chest.getZ();
        int chestDy = 2;

        // 1. Floor, the room, the ceiling.
        world.setBlockState(pos.set(x, floor, z), wall ? shell : vaultFloor(dx, dz),
                Block.NOTIFY_LISTENERS);
        for (int dy = 1; dy < VAULT_FLOOR - VAULT_CEIL; dy++) {
            if (onChest && dy == chestDy && !placeChest) continue;
            pos.set(x, floor + dy, z);
            if (wall) {
                BlockState state = vaultWall(dx, dz, dy);
                // The door and the chest are the two blocks a repair pass must not touch.
                if (state == null) {
                    if (!repairing) {
                        world.setBlockState(pos, ModBlocks.VAULT_DOOR.getDefaultState(),
                                Block.NOTIFY_LISTENERS);
                    }
                    continue;
                }
                world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            } else {
                world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        }
        // Ceiling: beams every four blocks, and an accent ring round the mouth of the stairwell.
        // Without the ring the opening, its lining and the stair are all the same masonry, and
        // the whole thing reads from the floor as a lump of rock hanging off the ceiling rather
        // than as a way up.
        boolean mouth = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        world.setBlockState(pos.set(x, ceiling, z),
                mouth || Math.floorMod(dx, 4) == 0 ? style.pillar : shell, Block.NOTIFY_LISTENERS);

        // 2. What stands on the floor of the two rooms.
        if (!wall) vaultFurniture(world, pos, x, z, dx, dz, floor, placeChest);

        // 3. The shaft and its stair, cut up through the ceiling to the dead end above.
        if (Math.abs(dx) > 2 || Math.abs(dz) > 2) return;
        if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
            // Line the rock the shaft is cut through. Below the ceiling it is already the room.
            for (int y = ceiling + 1; y < floorY; y++) {
                world.setBlockState(pos.set(x, y, z), shell, Block.NOTIFY_LISTENERS);
            }
            return;
        }
        int bottom = floor + 1;
        int rot = vaultSpiralRotation(bottom);
        for (int y = bottom; y <= floorY; y++) {
            world.setBlockState(pos.set(x, y, z), air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
        }
        for (int y = bottom; y <= floorY; y++) {
            for (int half = 0; half < 2; half++) {
                int[] step = SHAFT_SPIRAL[Math.floorMod((y - bottom) * 2 + half + rot,
                        SHAFT_SPIRAL.length)];
                if (step[0] != dx || step[1] != dz) continue;
                world.setBlockState(pos.set(x, y, z), vaultSlab()
                                .with(SlabBlock.TYPE, half == 0 ? SlabType.BOTTOM : SlabType.TOP),
                        Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * How far the stair's spiral is turned, so that its <b>top</b> step is the one under the
     * doorway of the dead end.
     *
     * <p>Without this the last step lands wherever the arithmetic puts it, which for three
     * orientations out of four is on the far side of the hole: you walk into the dead end and the
     * first thing you meet is the drop. The same mistake was made on the shaft of the surface
     * ruin and fixed there the same way.</p>
     */
    private int vaultSpiralRotation(int bottom) {
        int[] cell = vaultCell();
        int exit = cell == null ? -1 : layout().onlyExit(cell[0], cell[1]);
        int[] towards = switch (exit) {
            case MazeLayout.NORTH -> new int[]{0, -1};
            case MazeLayout.SOUTH -> new int[]{0, 1};
            case MazeLayout.WEST -> new int[]{-1, 0};
            case MazeLayout.EAST -> new int[]{1, 0};
            default -> null;
        };
        if (towards == null) return 0;
        int target = 0;
        for (int i = 0; i < SHAFT_SPIRAL.length; i++) {
            if (SHAFT_SPIRAL[i][0] == towards[0] && SHAFT_SPIRAL[i][1] == towards[1]) target = i;
        }
        // The last step is (floorY - bottom) * 2 + 1 half-steps along the spiral.
        return target - ((floorY - bottom) * 2 + 1);
    }

    /** Floor of the vault's rooms: the style's finer block, bordered by its masonry. */
    private BlockState vaultFloor(int dx, int dz) {
        boolean border = dx == VAULT_X0 + 1 || dx == VAULT_X1 - 1
                || dz == VAULT_Z0 + 1 || dz == VAULT_Z1 - 1
                || dz == VAULT_DOOR_Z - 1 || dz == VAULT_DOOR_Z + 1;
        return border ? style.wallBase : style.plaza;
    }

    /**
     * One block of one of the vault's walls, {@code dy} blocks above its floor.
     *
     * @return the block, or null for a block of the sealed door (handled by the caller, because a
     *         repair pass must leave the door exactly as it found it)
     */
    private BlockState vaultWall(int dx, int dz, int dy) {
        BlockState shell = style.wallBase;
        BlockState accent = style.pillar;
        boolean onKeys = dx >= VAULT_KEY_X0 && dx < VAULT_KEY_X0 + 12;

        if (dz == VAULT_Z0) {
            // The keypad wall: twelve keys in a row, framed above and below. No pilasters —
            // they would fall exactly where the keys go.
            if (onKeys && dy == VAULT_KEY_DY) {
                return ModBlocks.CODE_KEY.getDefaultState()
                        .with(CodeKeyBlock.KEY, dx - VAULT_KEY_X0)
                        .with(CodeKeyBlock.FACING, Direction.SOUTH);
            }
            if (onKeys && (dy == VAULT_KEY_DY - 1 || dy == VAULT_KEY_DY + 1)) return accent;
            // A light at each end of the row, on the only two columns the keys leave free.
            // Without them the nearest lantern is on a side wall ten blocks away and the middle
            // of the keypad sits at light 5, which is not enough to read a digit off a wall.
            if (dy == VAULT_KEY_DY && (dx == VAULT_KEY_X0 - 1 || dx == VAULT_KEY_X0 + 12)) {
                return style.light;
            }
            return shell;
        }
        if (dz == VAULT_DOOR_Z) {
            // The sealed door, three wide and three high, in an accent frame.
            if (Math.abs(dx) <= VAULT_DOOR_HALF && dy <= 3) return null;
            if (Math.abs(dx) <= VAULT_DOOR_HALF + 1 && dy == 4) return accent;
            if (Math.abs(dx) == VAULT_DOOR_HALF + 1 && dy <= 3) return accent;
            return shell;
        }
        if (dz == VAULT_Z1) {
            return Math.floorMod(dx, 4) == 0 ? accent : shell;
        }
        // The two side walls: pilasters every four blocks, a light between them.
        if (dy == 3 && (dz == -3 || dz == 1 || dz == VAULT_Z1 - 3)) return style.light;
        return Math.floorMod(dz, 4) == 0 ? accent : shell;
    }

    /**
     * What stands on the floor of the vault: the chest on its pedestal, and the two posts that
     * light it.
     */
    private void vaultFurniture(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                                int dx, int dz, int floor, boolean placeChest) {
        if (dz != VAULT_Z1 - 2) return;
        if (Math.abs(dx) <= 1) {
            world.setBlockState(pos.set(x, floor + 1, z), style.pillar, Block.NOTIFY_LISTENERS);
            if (dx == 0 && placeChest) {
                BlockPos at = new BlockPos(x, floor + 2, z);
                world.setBlockState(at, Blocks.CHEST.getDefaultState()
                        .with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
                if (world.getBlockEntity(at) instanceof ChestBlockEntity chest) {
                    chest.setLootTable(vaultLootTable(), seed ^ at.asLong());
                }
            }
            return;
        }
        if (Math.abs(dx) == 3) {
            world.setBlockState(pos.set(x, floor + 1, z), style.pillar, Block.NOTIFY_LISTENERS);
            world.setBlockState(pos.set(x, floor + 2, z), style.light, Block.NOTIFY_LISTENERS);
        }
    }

    /** Loot of the vault's chest: better than the maze's own, because it is harder to reach. */
    public static RegistryKey<LootTable> vaultLootTable() {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, MazeCraft.id("chests/vault"));
    }

    /**
     * The slab the vault's stair is built from. Taken from the style's own masonry wherever it
     * has a slab, and from its finer block where it has not — packed ice and basalt have none.
     */
    private BlockState vaultSlab() {
        return switch (style) {
            case HEDGE, JUNGLE, DARK_FOREST -> Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState();
            case DESERT -> Blocks.SANDSTONE_SLAB.getDefaultState();
            case SNOW -> Blocks.SPRUCE_SLAB.getDefaultState();
            case BADLANDS -> Blocks.RED_SANDSTONE_SLAB.getDefaultState();
            case CHERRY, SAVANNA -> Blocks.STONE_BRICK_SLAB.getDefaultState();
            case SWAMP -> Blocks.MUD_BRICK_SLAB.getDefaultState();
            case TAIGA -> Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState();
            case END -> Blocks.END_STONE_BRICK_SLAB.getDefaultState();
            case FORTRESS -> Blocks.NETHER_BRICK_SLAB.getDefaultState();
            case CRIMSON -> Blocks.CRIMSON_SLAB.getDefaultState();
            case WARPED -> Blocks.WARPED_SLAB.getDefaultState();
            case SOUL, BASALT -> Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState();
            case KRONOS -> Blocks.DEEPSLATE_BRICK_SLAB.getDefaultState();
        };
    }

    /**
     * Opens the vault: the nine blocks of the sealed door come down.
     *
     * <p>Nothing else changes. The room behind it was built with the maze, like the hoard, so
     * what the right code buys is a door and not a chamber appearing out of nothing.</p>
     */
    public void openVault(net.minecraft.server.world.ServerWorld world) {
        for (BlockPos door : vaultDoorBlocks()) {
            world.setBlockState(door, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
        }
    }

    /**
     * Asterion is down: the floor of the arena gives way onto the hoard.
     *
     * <p>Only the way down is cut here — eight blocks of shaft and its stair. The room itself
     * was built with the vault, because a chamber raised block by block from a tick would be
     * watched appearing through the hole.</p>
     *
     * <p>The stair is slabs, half a block to a step, and it starts on the chamber floor and
     * ends flush with the arena: the same two mistakes were made on the shaft of the surface
     * ruin — a first step a block and a half up, and a last step that needed a block placed to
     * finish the climb — and this one is laid out so that neither is possible.</p>
     */
    public void openHoard(net.minecraft.server.world.ServerWorld world) {
        int c = localCentre();
        int cx = originX() + c, cz = originZ() + c;
        int bottom = floorY - HOARD_FLOOR + 1;   // the chamber's walking level
        int top = floorY - 2;                    // the arena's floor block
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState brick = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = bottom; y <= top; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean lining = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    pos.set(cx + dx, y, cz + dz);
                    // Line only the rock the shaft is cut through; inside the chamber and the
                    // arena the surrounding blocks are already what they should be.
                    if (lining) {
                        if (y > floorY - HOARD_CEIL && y < top) {
                            world.setBlockState(pos, brick, Block.NOTIFY_LISTENERS);
                        }
                    } else {
                        world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
                    }
                }
            }
            for (int half = 0; half < 2; half++) {
                int[] step = SHAFT_SPIRAL[Math.floorMod((y - bottom) * 2 + half, SHAFT_SPIRAL.length)];
                pos.set(cx + step[0], y, cz + step[1]);
                world.setBlockState(pos, Blocks.DEEPSLATE_BRICK_SLAB.getDefaultState()
                        .with(SlabBlock.TYPE, half == 0 ? SlabType.BOTTOM : SlabType.TOP),
                        Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * Gold under the middle of the room, blackstone at its edges: a 7 × 7 floor of gold, 49
     * blocks, which is about what a bastion treasure room holds. Enough to read as a hoard the
     * moment the hole opens, and not so much that the rest of the game stops mattering.
     */
    private BlockState hoardFloor(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= 3
                ? Blocks.GOLD_BLOCK.getDefaultState()
                : Blocks.POLISHED_BLACKSTONE.getDefaultState();
    }

    /** What is heaped on this floor block, or null for bare floor. */
    private BlockState hoardHeap(int dx, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= 1) return null; // the foot of the stair
        // Mostly bronze, which is the vault's own metal and costs nothing; gold and emerald
        // sparingly. No diamond blocks: at one in a hundred, doubled when they stack, the worst
        // room came out at ninety diamonds, which is not a reward, it is a cheat code.
        int roll = hash(dx, dz, 3) % 100;
        if (roll < 11) return Blocks.OXIDIZED_COPPER.getDefaultState();
        if (roll < 18) return Blocks.RAW_GOLD_BLOCK.getDefaultState();
        if (roll < 21) return Blocks.EMERALD_BLOCK.getDefaultState();
        return null;
    }

    /** Stable pseudo-random from coordinates — never from the Random handed to a build pass. */
    private int hash(int a, int b, int c) {
        int h = (int) (seed ^ 0x9E3779B9L);
        h = h * 31 + a;
        h = h * 31 + b;
        h = h * 31 + c;
        h ^= h >>> 15;
        return Math.abs(h);
    }

    /**
     * One half of the double chest. Facing north, the LEFT half's partner is the block to its
     * east, so the two halves are at {@code chestX} and {@code chestX + 1}.
     */
    private void placeHoardChest(StructureWorldAccess world, BlockPos at, ChestType half) {
        world.setBlockState(at, Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, half),
                Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(at) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTableFor(MazeStyle.KRONOS, MazeSize.COLOSSAL), seed ^ at.asLong());
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
