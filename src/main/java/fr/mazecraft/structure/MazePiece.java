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
import net.minecraft.block.FallingBlock;
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
import net.minecraft.server.world.ServerWorld;
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
    /**
     * How far the foundation fills downward before giving up, in blocks.
     *
     * <p>12 until 1.0.0-alpha.10, and that is why mazes on sloping ground could end in a shelf
     * of flat ground hanging in the air. The fill walks down from the floor until it meets
     * solid rock, so on level terrain it costs nothing and on a slope it costs exactly what
     * the slope asks for — but at 12 it stopped short, and what it stopped short of was
     * visible from a long way off.</p>
     *
     * <p>Measured rather than guessed, over 37 sites that the placement rules accept as they
     * stand: two of them, one in eighteen, have ground further below their floor than the fill
     * could reach, and the worst drops <b>24</b> blocks — twelve blocks of air. 32 covers
     * every case measured with room to spare.</p>
     *
     * <p>The alternative was to make the placement rule stricter, by surveying the flattened
     * ring the build lays down and refusing sites whose ring is too steep. That was measured
     * too, and it costs three points of acceptance for hedge mazes and five for badlands and
     * cherry grove — to avoid a defect in one site out of eighteen. Deepening the fill costs
     * nothing at all: it does not change which sites are chosen, it does not touch the
     * bounding box (which deliberately starts above the floor, so that terrain adaptation
     * behaves), and on ground that needs no filling it writes no extra blocks. The cheaper fix
     * is also the one that keeps every maze the mod would otherwise have generated.</p>
     */
    public static final int FOUNDATION_DEPTH = 32;
    /** Height of the levers above the floor. */
    public static final int LEVER_DY = 2;

    private final MazeStyle style;
    private final MazeSize size;
    private final long seed;
    private final int floorY;
    private final int entranceSide;

    /**
     * Which level of the stack this piece is, counting <b>down</b> from 0 at the top, and how
     * many there are.
     *
     * <p>A maze is no longer one flat slab but several smaller floors stacked under each other,
     * and the way that is built is not by teaching this class to hold several floors — fifty-two
     * of its methods are written around having exactly one — but by placing <b>one piece per
     * floor</b>. A Minecraft structure already holds several pieces; that is how the building
     * and the maze under it live side by side. So each piece stays what it already is and is
     * already correct, a single floor, and the stack is made of three of them.</p>
     *
     * <p>What a piece does have to know is where it sits, because the levels where two floors
     * meet belong to both. With a pitch of {@link MazeSize#FLOOR_PITCH}, a floor's slab sits
     * exactly where the floor below's roof skin would go. So only the top piece lays a roof
     * skin and only the bottom one lays an underside; in between, the upper floor's own slab is
     * the lower floor's ceiling cover, which it already was.</p>
     *
     * <p>0 of 1 is a flat maze, which is every maze that exists today, and every rule below
     * reads as it did before at those values.</p>
     */
    private final int floorIndex;
    private final int floorCount;

    // Rebuilt lazily from the seed; not saved to NBT
    private MazeLayout layout;

    /**
     * How many floors a maze of this style and size is folded into.
     *
     * <p>Only a maze buried under a building. A surface maze is open to the sky and has no
     * burial problem to solve, and the Nether's hang over the lava sea and are walked into
     * through a doorway — stacking either would change what they are to fix something they do
     * not suffer from. {@code enteredFromPlaza} is exactly the set that does.</p>
     */
    public static int floorsFor(MazeStyle style, MazeSize size) {
        return style.enteredFromPlaza() ? size.floors() : 1;
    }

    /**
     * Cells per side of <b>one floor</b> of this maze, which is the footprint everything else
     * has to agree on.
     *
     * <p>This is the whole of the switch to floors, and it has to live in exactly one place.
     * A maze that is folded into a stack occupies its per-floor footprint; one that is not —
     * every surface style, the five of the Nether, and SMALL, which stays flat — occupies the
     * one it always did. Two answers to "how wide is this maze" is how the surveys end up
     * deciding where a 149-wide maze may go and then an 85-wide one gets built there.</p>
     */
    public static int cellsOf(MazeStyle style, MazeSize size) {
        return floorsFor(style, size) > 1 ? size.cellsPerFloor() : size.cells();
    }

    /** Side of one floor's footprint in blocks. */
    public static int spanOf(MazeStyle style, MazeSize size) {
        return cellsOf(style, size) * MazeLayout.CELL + 1;
    }

    /** Blocks from the top floor's slab down to the bottom one's. */
    public static int stackDrop(MazeStyle style, MazeSize size) {
        return MazeSize.FLOOR_PITCH * (floorsFor(style, size) - 1);
    }

    /** The topmost floor: the one the building's staircase arrives in, and the only one with a plaza. */
    public boolean isTopFloor() {
        return floorIndex == 0;
    }

    /** The deepest floor: the one that carries the crypt, the guardian and the treasure. */
    public boolean isBottomFloor() {
        return floorIndex == floorCount - 1;
    }

    public int floorIndex() {
        return floorIndex;
    }

    /**
     * @param entranceSide {@link MazeLayout#NORTH}.. {@link MazeLayout#EAST}, or -1 for random
     */
    public MazePiece(MazeStyle style, MazeSize size, long seed, int centerX, int floorY, int centerZ, int entranceSide) {
        this(style, size, seed, centerX, floorY, centerZ, entranceSide, 0, 1);
    }

    public MazePiece(MazeStyle style, MazeSize size, long seed, int centerX, int floorY, int centerZ,
                     int entranceSide, int floorIndex, int floorCount) {
        super(ModStructures.MAZE_PIECE, 0, boxFor(style, size, centerX, floorY, centerZ));
        this.style = style;
        this.size = size;
        this.seed = seed;
        this.floorY = floorY;
        this.entranceSide = entranceSide;
        this.floorIndex = floorIndex;
        this.floorCount = floorCount;
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
        // Absent on every piece written before mazes had floors, and the defaults are exactly
        // what such a piece is: the only floor of a stack of one.
        this.floorIndex = nbt.contains("Floor") ? nbt.getInt("Floor") : 0;
        this.floorCount = nbt.contains("Floors") ? nbt.getInt("Floors") : 1;
    }

    private static BlockBox boxFor(MazeStyle style, MazeSize size, int centerX, int floorY, int centerZ) {
        int half = spanOf(style, size) / 2;
        int minX = centerX - half - style.margin;
        int minZ = centerZ - half - style.margin;
        int side = spanOf(style, size) + 2 * style.margin;
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
        nbt.putInt("Floor", floorIndex);
        nbt.putInt("Floors", floorCount);
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
        int centre = spanOf(style, size) / 2;
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
        return spanOf(style, size) / 2;
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

    // =====================================================================
    // The crypt: the room under the goal of a maze entered through its middle
    // =====================================================================

    /**
     * Half-width of the crypt, and how far below the maze floor it sits.
     *
     * <p>Nineteen blocks across outside, seventeen of room, and seven blocks of head room —
     * taller than the vault's four, and more than twice its floor, because something is going to
     * be fought in here and, since the stair falls away when the fight starts, there is nowhere
     * to fight it from except the floor. Thirteen by thirteen was the arena of Kronos, which is
     * the same size sunk into a plaza four times wider; a closed box of that size is not the
     * same room.</p>
     *
     * <p>The ceiling stays at the vault's five below the maze floor, so the stair down is the
     * same length and reads the same; the floor goes deeper instead. Seven of head room and not
     * ten on purpose: the collapse drops whoever is on the spiral, and the room's height is
     * exactly how far they fall. Seven costs about three hearts, ten would cost five.</p>
     *
     * <p>How big it may be is bounded, and not by taste: {@code MazeLayout.GOAL_CLEARANCE} keeps
     * the goal dead end three cells — fourteen blocks — from the outer wall, and a column
     * outside the maze's own bounding box is never visited by the build pass at all. Radius plus
     * the skin must stay under that, so the ceiling is 13.</p>
     */
    private static final int CRYPT_RADIUS = 9;
    private static final int CRYPT_FLOOR = 13, CRYPT_CEIL = 5;

    /** Stands in the cache for "asked, and this maze has no crypt". */
    private static final int[] NO_CRYPT = new int[0];
    private int[] cryptCell;

    /** True for a maze whose progression leads outwards to a dead end with a room under it. */
    public boolean hasCrypt() {
        // One crypt per maze, at the bottom of the stack. Every floor has a goal -- that is
        // what its walk ends at -- but only the last one's goal is the end of the maze.
        if (!isBottomFloor()) return false;
        return style.enteredFromPlaza();
    }

    /**
     * The dead end the crypt hangs under: the goal of the progression, behind the last gate.
     *
     * <p>Cached the same way, and for the same reason, as {@link #vaultCell()}: one field, one
     * write, {@link #NO_CRYPT} standing in for "there is none". Chunks of one maze generate on
     * several threads, and a value field plus a "known" flag can be seen half-written.</p>
     */
    private int[] cryptCell() {
        int[] cached = cryptCell;
        if (cached == null) {
            int[] found = hasCrypt() ? layout().goal() : null;
            cached = found == null ? NO_CRYPT : found;
            cryptCell = cached;
        }
        return cached == NO_CRYPT ? null : cached;
    }

    /** Local X/Z of the middle of the crypt's dead-end cell, or null. */
    private int[] cryptOrigin() {
        int[] cell = cryptCell();
        return cell == null ? null
                : new int[]{MazeLayout.CELL * cell[0] + 2, MazeLayout.CELL * cell[1] + 2};
    }

    /** The middle of the crypt floor, where the treasure stands. */
    public BlockPos cryptChestPos() {
        int[] o = cryptOrigin();
        return o == null ? null : new BlockPos(
                originX() + o[0], floorY - CRYPT_FLOOR + 1, originZ() + o[1]);
    }

    /** True for the nine columns of the crypt's stairwell: the 3 × 3 around its middle. */
    private boolean isCryptShaft(int lx, int lz) {
        if (!hasCrypt()) return false;
        int[] o = cryptOrigin();
        return o != null && Math.abs(lx - o[0]) <= 1 && Math.abs(lz - o[1]) <= 1;
    }

    /** The dead end at the top of the crypt's stair, at feet height. */
    public BlockPos cryptEntrance() {
        int[] o = cryptOrigin();
        return o == null ? null : new BlockPos(originX() + o[0], floorY + 1, originZ() + o[1]);
    }

    /**
     * Puts the crypt's chest on its floor. Called when the guardian falls, not when the maze is
     * built — the chest is meant to arrive, not to have been waiting.
     *
     * <p>Idempotent, because the two things that call it are a death and a fallback and either
     * may run twice: it does nothing if a chest is already there. The repair pass never writes
     * this block at all, so a chunk reloading afterwards leaves it alone.</p>
     *
     * @return true if a chest was actually placed
     */
    public boolean placeCryptChest(ServerWorld world) {
        BlockPos at = cryptChestPos();
        if (at == null || world.getBlockState(at).isOf(Blocks.CHEST)) return false;
        world.setBlockState(at, Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(at) instanceof ChestBlockEntity chest) {
            chest.setLootTable(cryptLootTableFor(style, size), seed);
        }
        return true;
    }

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
            layout = layoutFor(floorIndex);
        }
        return layout;
    }

    /**
     * The cell of this floor where the staircase from the floor above comes down, or null on
     * the top floor, which is entered by the building's own shaft instead.
     *
     * <p>It is the <b>goal</b> of the floor above: the dead end its walk ends at. Which is the
     * same cell this floor's progression is rooted at, so the player arrives where this floor
     * begins.</p>
     */
    private int[] stairCell() {
        if (isTopFloor()) return null;
        if (stairCell == null) {
            int[] found = parentLayout().goal();
            stairCell = found == null ? NO_CRYPT : found;
        }
        return stairCell == NO_CRYPT ? null : stairCell;
    }

    private int[] stairCell;

    /**
     * The floor above's layout, kept rather than recomputed.
     *
     * <p>{@link #layoutFor} walks the stack from the top every time it is called, so asking it
     * for the parent once per column of a stairwell would regenerate two whole mazes nine times
     * over for every chunk the stair touches. It is the same answer each time; hold it.</p>
     */
    private MazeLayout parentLayout;

    private MazeLayout parentLayout() {
        if (parentLayout == null) parentLayout = layoutFor(floorIndex - 1);
        return parentLayout;
    }

    /** The spiral's phase, which is one number per piece and never changes. */
    private int floorRot = Integer.MIN_VALUE;

    /**
     * The layout of floor {@code index}, worked out from the maze's seed alone.
     *
     * <p>A lower floor is rooted where its staircase arrives, which is the goal of the floor
     * above, so each one depends on the one before it. Rather than hand that down from piece to
     * piece — which would mean the three pieces of a stack had to be built in order, and that
     * chunk generation would have to guarantee it — every piece simply recomputes the floors
     * above its own. It is deterministic from the seed, it is a handful of array walks, and it
     * happens once per piece because {@link #layout} is cached. Independence is worth more here
     * than the few microseconds it costs.</p>
     */
    private MazeLayout layoutFor(int index) {
        MazeLayout top = MazeLayout.generate(cellsOf(style, size), seed, entranceSide, size.gates(),
                plazaRadius(), style.enteredFromPlaza());
        if (index == 0) return top;
        MazeLayout current = top;
        for (int f = 1; f <= index; f++) {
            int[] root = current.goal();
            if (root == null) return current;   // no progression: nothing to descend from
            boolean last = f == floorCount - 1;
            current = MazeLayout.generateFloor(cellsOf(style, size), seed * 31 + f, size.gates(),
                    root[0], root[1], last ? CRYPT_GOAL_CLEARANCE : STAIR_GOAL_CLEARANCE);
        }
        return current;
    }

    /**
     * Room the goal dead end keeps from the outer wall, in cells.
     *
     * <p>Three on the bottom floor, because the crypt is dug under its goal and a crypt that
     * overhangs the footprint is not clipped neatly — it is simply missing on that side. One
     * anywhere else, where the goal is a 3 × 3 stairwell and three cells of margin would throw
     * away most of the candidate dead ends on a grid this small for nothing.</p>
     */
    private static final int CRYPT_GOAL_CLEARANCE = 3;
    private static final int STAIR_GOAL_CLEARANCE = 1;

    /** World X of local maze coordinate 0 (the maze proper, not the margin). */
    private int originX() {
        return boundingBox.getMinX() + style.margin;
    }

    private int originZ() {
        return boundingBox.getMinZ() + style.margin;
    }

    /** World position of the central chest. */
    /**
     * Where the treasure is.
     *
     * <p>The middle of the maze for the sixteen styles you walk into from outside — you work
     * your way in, and the plaza is the prize. For a maze entered <em>through</em> its middle
     * the middle is the front door, so the prize goes to the far end instead: down the stair in
     * the dead end furthest from the plaza, on the floor of the crypt. Measured over two
     * thousand layouts, that dead end is 61 to 285 cells of walking in a medium maze.</p>
     *
     * <p>One method, so the thirteen places that ask where the chest is follow without knowing
     * any of this. What they must not use it for is the maze's geometric middle — see
     * {@link #centerPos}.</p>
     */
    public BlockPos chestPos() {
        BlockPos crypt = cryptChestPos();
        return crypt != null ? crypt : centerPos();
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

    /**
     * The one block of the doorway that wears the keyhole, or null for a maze with no doorway.
     *
     * <p>The middle column, two blocks up. Not the centre of the plane, because there is no
     * such block: the doorway is three wide and {@link #WALL_HEIGHT} — four — high, and a
     * three-by-three door would leave a gap along the top that a player can crawl through. Two
     * up is eye level for someone standing in front of it, which is where a hand goes.</p>
     */
    public BlockPos keyholePos() {
        int[][] cells = layout().entranceGap();
        if (cells.length == 0) return null;
        int[] middle = cells[cells.length / 2];
        return new BlockPos(originX() + middle[0], floorY + 2, originZ() + middle[1]);
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
        if (target < 0) {
            // Every gate open, so the thread now points at the end of the road. Which end that
            // is depends on which way the maze runs: inwards to the plaza for the fifteen
            // surface styles, outwards to the goal dead end — and the crypt under it — for a
            // maze entered through its own middle. This line was the inversion's last stone
            // left unturned: it sent the player back to the middle they had walked in from.
            int[] goal = hasCrypt() ? cryptOrigin() : null;
            target = goal != null ? goal[0] * span + goal[1]
                    : layout.center() * span + layout.center();
        }

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

    /**
     * The geometric middle of the maze, at feet height.
     *
     * <p>Not the chest any more, and the difference matters in two places: the barrier shoves a
     * player who may not enter <b>away from the middle</b>, and pushing them away from a chest
     * sitting in a far corner would shove some of them further in. Anything that means
     * "the plaza" or "the centre" asks this; anything that means "the treasure" asks
     * {@link #chestPos}.</p>
     */
    public BlockPos centerPos() {
        int c = layout().center();
        return new BlockPos(originX() + c, floorY + 1, originZ() + c);
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
    /**
     * The crypt's own table: everything the maze's chest holds, plus a chance at this biome's
     * key fragment.
     *
     * <p>A table of its own per style and size, rather than a pool bolted onto the shared one,
     * because {@link #lootTableFor} resolves to {@code chests/maze_<size>} for every Overworld
     * style alike — there is nowhere in it to say "the cherry fragment". These tables are
     * generated: each is the shared table referenced whole, plus one pool holding the one
     * fragment at the odds its size earns.</p>
     *
     * <p>Only the nine styles that carry a fragment have one, and only those nine have a crypt
     * to put it in.</p>
     */
    public static RegistryKey<LootTable> cryptLootTableFor(MazeStyle style, MazeSize size) {
        if (!style.hasFragment()) return lootTableFor(style, size);
        return RegistryKey.of(RegistryKeys.LOOT_TABLE,
                MazeCraft.id("chests/crypt/" + style.id() + "_" + size.id()));
    }

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

                // The crypt's stairwell, left EXACTLY as it is found on a repair — the whole
                // column, every pass, not just the slabs. The stair is state now: it falls away
                // when the guardian is met and is written again when it dies (see
                // collapseCryptStair), and a pass that knows nothing about that state must not
                // touch it.
                //
                // It has to be here and not inside buildCrypt, which was the first attempt and
                // destroyed the stair rather than preserving it. Four separate writes land in
                // this column before buildCrypt is ever called or after it starts: the maze's
                // own floor closes the top of the shaft at floorY, buildUnderside closes it a
                // block lower, emptying the crypt airs the slabs between the floor and the
                // ceiling, and the ceiling row plugs the mouth with style.pillar. At generation
                // the spiral runs last and takes all four back; skip the spiral alone and they
                // all stand, which seals the crypt on the first chunk reload.
                //
                // Nine columns in the middle of one dead end. Nothing above them is reachable
                // by the decoration spillage this pass exists to clear.
                if (repairing && isCryptShaft(lx, lz)) continue;

                boolean inside = layout.isInside(lx, lz);
                boolean isWall = inside && layout.isWall(lx, lz);
                boolean isPillar = inside && layout.isPillar(lx, lz);
                boolean isPlaza = inside && layout.isPlaza(lx, lz);

                // 1. Foundation. Enclosed (Nether): a clean underside slab + fortress-like support
                //    pillars down to the ground or into the lava. Overworld: fill gaps until solid ground.
                if (style.enclosed) {
                    // Only the ends of the stack. A floor's slab sits exactly where the floor
                    // below's roof skin would go, so in between the two would write the same
                    // level with different blocks; the upper floor's own slab already covers
                    // the lower floor's ceiling.
                    if (isBottomFloor()) buildUnderside(world, pos, x, z);
                    if (isTopFloor()) buildShell(world, pos, x, z);
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

                // 2e. The crypt at the end of a maze entered through its middle. After the floor
                //     above, because its stair is cut up through that floor.
                if (hasCrypt()) buildCrypt(world, pos, x, z, lx, lz, placeChest);

                // 3. Walls, pillars, and clearing above
                if (style.enclosed) {
                    // The ring of a landmark maze is rock, not corridor. Done here rather than
                    // with the rest of the shell because step 2 above has just written a floor
                    // across it, and the ring has no floor.
                    if (!inside && style.enteredFromPlaza()) {
                        fillRockRing(world, pos, x, z, keep);
                    } else {
                        buildEnclosedColumn(world, pos, x, z, lx, lz, inside, isWall, isPillar, air, keep);
                    }
                    // After the walls, never with the crypt: the crypt is dug below the floor
                    // and nothing up here touches it, but this stair stands exactly where step 3
                    // lays corridor and wall, so running it earlier would see its own work
                    // written over.
                    buildFloorStair(world, pos, x, z, lx, lz);
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
                    if (current != target
                            && !(repairing && current.isOf(Blocks.SNOW) && target.isAir())
                            && !(repairing && isMarker(current) && target.isAir())) {
                        // SKIP_DROPS: this pass replaces whatever stands in the corridor with
                        // air, and on a repair what stands there includes the maze's own
                        // fittings. Without it a lever is broken into an item and laid again by
                        // step 5, which is why one turns up on the floor under its own wall.
                        world.setBlockState(pos, target, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
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
            BlockPos keyhole = tier == SealedGatewayBlock.Tier.KRONOS ? keyholePos() : null;
            for (BlockPos gatewayPos : entranceBlocks()) {
                if (chunkBox.contains(gatewayPos)) {
                    world.setBlockState(gatewayPos, gateway.with(SealedGatewayBlock.KEYHOLE,
                            gatewayPos.equals(keyhole)), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // 5. Levers, each hanging on a log set into the hedge (leaves can't hold a lever)
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (MazeLayout.Lever lever : layout.levers()) {
            pos.set(originX() + lever.supportX(), floorY + LEVER_DY, originZ() + lever.supportZ());
            if (chunkBox.contains(pos)) world.setBlockState(pos, style.pillar, Block.NOTIFY_LISTENERS);
            pos.set(originX() + lever.x(), floorY + LEVER_DY, originZ() + lever.z());
            // A lever that is already there is left alone on a repair, rather than written
            // again as a fresh unpulled one: whether it has been thrown is state, and the
            // default state would quietly flip it back up under the player's hand.
            if (chunkBox.contains(pos)
                    && !(repairing && world.getBlockState(pos).isOf(Blocks.LEVER))) {
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
        // A crypt's chest is not built with the maze: it arrives when the guardian falls, from
        // placeCryptChest. Kronos's hoard is the same idea and has always worked that way.
        BlockPos chest = chestPos();
        // isBottomFloor, and without it every floor of a stack got one. The rule reads "a maze
        // with no crypt keeps its treasure in the middle", and it was true while a maze was one
        // floor. Once hasCrypt answered only for the deepest floor, the floors above it became
        // mazes with no crypt as far as this line could tell -- so each laid a full treasure
        // chest, diamonds and enchanted gear, in the middle of a corridor. The treasure belongs
        // to the maze, and the maze ends at the bottom.
        if (placeChest && !style.isKronos() && isBottomFloor() && !hasCrypt()
                && chunkBox.contains(chest)) {
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

    /**
     * One column of the crypt: the room at the end of a maze entered through its middle.
     *
     * <p>A single chamber rather than the vault's two, because nothing here is behind a door —
     * the lock on this room is the maze above it. You walk the longest path the maze has, you
     * find the dead end at the end of it, and the floor of that dead end opens.</p>
     *
     * <p>Written to the same rules as the vault and the hoard, which are not stylistic: a room
     * this size cannot be raised from a tick, so it is built with the chunk; and nothing in it
     * may depend on the {@code Random} handed to the build pass, because generation and the
     * repair pass are seeded differently and the room would rearrange itself the first time a
     * chunk was repaired. And the chest's own block is never written on a repair — writing it
     * scatters the contents on the floor and leaves no chest, which is the bug the hoard shipped
     * with and the vault then inherited.</p>
     */
    private void buildCrypt(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                            int lx, int lz, boolean placeChest) {
        int[] o = cryptOrigin();
        if (o == null) return;
        int dx = lx - o[0], dz = lz - o[1];
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d > CRYPT_RADIUS + 1) return;

        int floor = floorY - CRYPT_FLOOR;
        int ceiling = floorY - CRYPT_CEIL;

        // The sides of the skin, one block out from the wall. The floor and ceiling were sealed
        // when the rooms got their rock, and the sides were missed — which left the one thing
        // the seal exists for still exposed: the wall's pilasters are {@code style.pillar}, logs
        // for the cherry style, and a lava flow in the cave outside meets them directly. Stone
        // on four sides and kindling on the other two is not a sealed room.
        if (d == CRYPT_RADIUS + 1) {
            for (int y = floor - 1; y <= ceiling + 1; y++) seal(world, pos, x, y, z);
            return;
        }
        BlockState shell = style.wallBase;
        BlockState air = Blocks.AIR.getDefaultState();
        boolean wall = d == CRYPT_RADIUS;

        BlockPos chest = cryptChestPos();
        boolean onChest = !wall && x == chest.getX() && z == chest.getZ();

        // 1. Floor, room, ceiling.
        world.setBlockState(pos.set(x, floor, z),
                wall ? shell : (d >= CRYPT_RADIUS - 1 ? shell : style.plaza), Block.NOTIFY_LISTENERS);
        for (int y = floor + 1; y < ceiling; y++) {
            if (onChest && y == chest.getY() && !placeChest) continue;
            pos.set(x, y, z);
            if (wall) {
                boolean pilaster = (Math.abs(dx) == CRYPT_RADIUS && Math.floorMod(dz, 4) == 0)
                        || (Math.abs(dz) == CRYPT_RADIUS && Math.floorMod(dx, 4) == 0);
                world.setBlockState(pos, pilaster ? style.pillar : shell, Block.NOTIFY_LISTENERS);
            } else {
                // SKIP_DROPS as well: emptying a room must never spill anything on its floor,
                // whatever ends up standing in it one day.
                world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        }
        // The mouth of the stairwell gets the accent block, or the opening, its lining and the
        // stair are all the same masonry and read from below as a lump hanging off the ceiling.
        boolean mouth = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        world.setBlockState(pos.set(x, ceiling, z),
                mouth || Math.floorMod(dx, 4) == 0 ? style.pillar : shell, Block.NOTIFY_LISTENERS);
        // A block of rock under the floor and over the ceiling: both of those are made of the
        // style's own wood here. The shaft is cut after this and takes its own hole back.
        seal(world, pos, x, floor - 1, z);
        seal(world, pos, x, ceiling + 1, z);

        if (!wall) {
            // Lights hung off the diagonals, clear of the stair and of the middle: two rings
            // of four now the room is seventeen across, or the far corners sit in the dark.
            if (Math.abs(dx) == Math.abs(dz) && (Math.abs(dx) == 4 || Math.abs(dx) == 8)) {
                world.setBlockState(pos.set(x, ceiling - 1, z),
                        style.hangingLight != null ? style.hangingLight : style.light,
                        Block.NOTIFY_LISTENERS);
            }
        }

        // 2. The shaft up to the dead end, and its spiral.
        if (Math.abs(dx) > 2 || Math.abs(dz) > 2) return;
        if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
            for (int y = ceiling + 1; y < floorY; y++) {
                world.setBlockState(pos.set(x, y, z), shell, Block.NOTIFY_LISTENERS);
            }
            return;
        }
        int bottom = floor + 1;
        int rot = cryptSpiralRotation(bottom);
        // Top down would pull the lantern hanging under the dead end's ceiling off its support,
        // the way the pagoda's shaft did; nothing hangs here yet, but the order costs nothing.
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
     * The staircase from the floor above, cut by the floor <b>below</b> it.
     *
     * <p>Which way round matters, and getting it wrong is a bug this project has already had
     * once: the pieces of a structure generate in the order they were added, so if the upper
     * floor cut its own way down, the lower floor would build afterwards and plug it — the same
     * way the building's shaft was once sealed by the roof it led to. The lower floor writing
     * last is what keeps the hole open, on generation and on every repair alike.</p>
     *
     * <p>Six blocks of descent, which is {@link MazeSize#FLOOR_PITCH}: from the slab of the
     * floor above down to this floor's own walking surface, through that slab, through this
     * floor's ceiling, and into its corridor. Twelve half-steps on the same ring the crypt's
     * stair and the landmarks' staircases wind round.</p>
     */
    private void buildFloorStair(StructureWorldAccess world, BlockPos.Mutable pos,
                                 int x, int z, int lx, int lz) {
        int[] cell = stairCell();
        if (cell == null) return;
        int ox = MazeLayout.CELL * cell[0] + 2, oz = MazeLayout.CELL * cell[1] + 2;
        int dx = lx - ox, dz = lz - oz;
        // One, not two. Two is the wall line between cells, and whatever is written there --
        // masonry or air -- is wrong: masonry bricks up the cell's doorways, air punches holes
        // through walls the maze meant to keep. The well is the cell and nothing more.
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return;

        int bottom = floorY + 1;                        // this floor's walking surface
        int top = floorY + MazeSize.FLOOR_PITCH;        // the slab of the floor above

        // No lining, and the first version's was a mistake worth recording. It wrote
        // style.wallBase over every column at |dx| == 2 or |dz| == 2, on the reasoning that the
        // well crosses this floor's corridors and would otherwise open sideways into them.
        //
        // It does not cross anything. A cell's corridor runs from CELL*c + 1 to CELL*c + 3, so
        // the 3 x 3 well IS the cell, exactly -- and |d| == 2 is the wall line, where the cell's
        // four DOORWAYS are cut. The lining therefore bricked up every way out of the cell the
        // player had just climbed down into. Measured afterwards on 300 generated stacks: 300
        // arrival cells with no exit at all, 605 doorways walled. And since wallBase is stone
        // brick, it also stamped a grey 5 x 5 box through corridors made of cherry leaves.
        //
        // The cell is already walled, by the maze. Nothing here needs a wall of its own.
        // Bottom upwards, so a block is cleared before the one above it: clearing a cell can
        // dislodge whatever hangs beneath it, and the order is the fix rather than a list of
        // blocks to watch for.
        for (int y = bottom; y <= top; y++) {
            // A newel post down the middle, as the buildings' own staircases have. Without it
            // the well is an open shaft with a rim of steps, which invites dropping straight
            // down rather than walking it and reads as slabs stuck to a wall instead of a
            // stair. The eight cells around it stay open, so it cannot block the corridor.
            world.setBlockState(pos.set(x, y, z),
                    dx == 0 && dz == 0 ? style.pillar : Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
        }
        if (floorRot == Integer.MIN_VALUE) floorRot = floorSpiralRotation(bottom, top);
        int rot = floorRot;
        for (int y = bottom; y <= top; y++) {
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
     * Turned so the <b>bottom</b> step faces a way out of the cell it lands in.
     *
     * <p>Only one end can be chosen, and which is not a matter of taste. Twelve half-steps put
     * the two ends eleven apart on a ring of eight, so they differ by three -- an odd number,
     * which flips the parity. A cell's four doorways are the four <em>orthogonal</em> cells of
     * that ring; the other four are its corners. Anchoring one end on a doorway therefore puts
     * the other on a corner, always, and no phase spares both.</p>
     *
     * <p>It used to anchor the top. Measured afterwards over 400 stacks, the bottom step then
     * faced a way out <b>0 times out of 400</b> -- not bad luck but arithmetic: the player came
     * down six blocks and was set down in a corner, facing a wall. Anchoring the bottom costs
     * only that the top step sits in a corner of a dead end the player is already standing in,
     * with the whole mouth of the well open in front of them.</p>
     */
    private int floorSpiralRotation(int bottom, int top) {
        int[] cell = stairCell();
        if (cell == null) return 0;
        int ox = MazeLayout.CELL * cell[0] + 2, oz = MazeLayout.CELL * cell[1] + 2;
        int[] towards = null;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!layout().isWall(ox + 2 * d[0], oz + 2 * d[1])) {
                towards = d;
                break;
            }
        }
        if (towards == null) return 0;
        int target = 0;
        for (int i = 0; i < SHAFT_SPIRAL.length; i++) {
            if (SHAFT_SPIRAL[i][0] == towards[0] && SHAFT_SPIRAL[i][1] == towards[1]) target = i;
        }
        // The bottom step's index is the phase itself: (y - bottom) * 2 + half is 0 there.
        return target;
    }

    /** Turned so the TOP step of the crypt's stair is the one under the dead end's doorway. */
    private int cryptSpiralRotation(int bottom) {
        int[] cell = cryptCell();
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
        return target - ((floorY - bottom) * 2 + 1);
    }

    /**
     * The eight cells round a 3 × 3 shaft: one step per half block makes a walkable spiral.
     *
     * <p>The same ring the landmarks' staircases wind round — {@link SlabSpiral} shows that the
     * pagoda's hand-built stair is this one with a direction and a phase. Held in one place so
     * the two cannot drift; the index arithmetic below is still written out here, which is a
     * duplication worth removing one day but not while it works.</p>
     */
    private static final int[][] SHAFT_SPIRAL = SlabSpiral.RING;

    /**
     * True when {@code pos} is inside the crypt's room — not its stairwell, not its walls.
     *
     * <p>The room, deliberately: the stair is what this is used to take away, so "is somebody in
     * here" must not become true because somebody is standing on the stair. The test is the
     * open volume between the floor and the underside of the ceiling, inside the walls.</p>
     */
    public boolean isInCryptRoom(BlockPos pos) {
        int[] o = cryptOrigin();
        if (o == null) return false;
        if (pos.getY() < floorY - CRYPT_FLOOR || pos.getY() >= floorY - CRYPT_CEIL) return false;
        return Math.abs(pos.getX() - (originX() + o[0])) < CRYPT_RADIUS
                && Math.abs(pos.getZ() - (originZ() + o[1])) < CRYPT_RADIUS;
    }

    /**
     * Takes the crypt's stair away, and puts it back.
     *
     * <p>Why there is a mechanism here at all: the spiral is a perch. It is a block wide and
     * climbs round a pole, which nothing with a hitbox can follow, so a player standing three
     * steps up fights the guardian from somewhere it can never reach and the fight is not a
     * fight. The first fix that suggests itself — seal the top of the stairwell when the
     * guardian wakes — fixes nothing, because the player is already on the stairs when it
     * closes and the stairs are the problem.</p>
     *
     * <p>So the stair goes. Whoever is on it drops into the room, which is the whole height of
     * the room at worst and about three hearts, and the chimney above is left smooth: no way
     * back up until the guardian falls, at which point {@link #restoreCryptStair} writes the
     * spiral again alongside the chest and the fireworks.</p>
     *
     * <p>It cannot be done at the moment the guardian is spawned, which is when the last lever
     * is pulled, minutes of walking away: the stair would be gone before anybody had used it and
     * the crypt would be a hole nobody could get out of. It happens when a player is actually in
     * the room — see {@code ChampionTracker.shutTheCrypt}.</p>
     *
     * @return true if anything actually changed, so the caller only makes a noise about it once
     */
    public boolean collapseCryptStair(ServerWorld world) {
        return writeCryptStair(world, false);
    }

    /** Puts the spiral back, when the guardian of the crypt has fallen. */
    public boolean restoreCryptStair(ServerWorld world) {
        return writeCryptStair(world, true);
    }

    private boolean writeCryptStair(ServerWorld world, boolean present) {
        int[] o = cryptOrigin();
        if (o == null) return false;
        int bottom = floorY - CRYPT_FLOOR + 1;
        // The same rotation the build pass used, from the same inputs, so the restored stair is
        // the one that was there rather than one that happens to fit.
        int rot = cryptSpiralRotation(bottom);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        boolean stairMoved = false;
        for (int y = bottom; y <= floorY; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // The step that belongs in this cell, worked out whether it is going in or
                    // coming out, because telling the two apart is what the return value is for.
                    BlockState step = null;
                    for (int half = 0; half < 2; half++) {
                        int[] cell = SHAFT_SPIRAL[Math.floorMod((y - bottom) * 2 + half + rot,
                                SHAFT_SPIRAL.length)];
                        if (cell[0] != dx || cell[1] != dz) continue;
                        step = vaultSlab().with(SlabBlock.TYPE,
                                half == 0 ? SlabType.BOTTOM : SlabType.TOP);
                    }
                    BlockState want = present && step != null ? step : air;
                    pos.set(originX() + o[0] + dx, y, originZ() + o[1] + dz);
                    BlockState current = world.getBlockState(pos);
                    if (current.equals(want)) continue;
                    // Only a STEP going in or coming out counts as the stair moving. Everything
                    // else in the shaft is still swept away — it has to be, or the player builds
                    // their way back up — but silently.
                    //
                    // Reporting every change made the collapse announce itself over and over: a
                    // snow golem knocked into the shaft leaves snow layers, and a block placed
                    // in it is a block, and each one was a difference this pass dutifully
                    // cleared and called "the stair crumbles behind you". Five ticks later,
                    // again.
                    if (step != null && (present || current.equals(step))) stairMoved = true;
                    // SKIP_DROPS: a slab taken out from under a player must not become an item.
                    world.setBlockState(pos, want, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                }
            }
        }
        return stairMoved;
    }

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
        if (dx < VAULT_X0 - 1 || dx > VAULT_X1 + 1 || dz < VAULT_Z0 - 1 || dz > VAULT_Z1 + 1) return;

        int floor = floorY - VAULT_FLOOR;
        int ceiling = floorY - VAULT_CEIL;

        // The sides of the skin, one block out, for the same reason as the crypt's: this room's
        // floor is style.plaza and its ceiling carries style.pillar beams, both of which are
        // wood for the cherry style, and only the top and bottom of it were ever sealed.
        if (dx < VAULT_X0 || dx > VAULT_X1 || dz < VAULT_Z0 || dz > VAULT_Z1) {
            for (int y = floor - 1; y <= ceiling + 1; y++) seal(world, pos, x, y, z);
            return;
        }

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
        seal(world, pos, x, floor - 1, z);
        seal(world, pos, x, ceiling + 1, z);

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
        // A maze buried in the Overworld is standing in rock, so it needs no plate and no
        // posts: one block of fill where there is a hole, exactly as the roof already does.
        //
        // What it did before was write the plate unconditionally, for every column of the box
        // -- 101 by 101 for a medium, 149 by 149 for a large. Invisible in solid ground, and in
        // a cliff, a cave or a river bed it is a perfectly flat, perfectly square ceiling of
        // one stone: the big grey rectangle in the rock face. The pillars did the same lower
        // down, dropping up to eighty blocks of post through open air.
        //
        // The plate was written for the Nether, where the maze really does hang over the lava
        // sea and really does need a floor on legs, fortress-fashion. Keeping it there and
        // dropping it here is not a compromise, it is what each case actually wants. Burying
        // the maze deeper would not have helped: the plate shows because it is written whether
        // it is needed or not, not because there is too little rock above it.
        if (!style.isNether()) {
            seal(world, pos, x, floorY - 1, z);
            return;
        }
        BlockBox box = this.boundingBox;
        BlockState slab = style.substructure();
        pos.set(x, floorY - 1, z);
        if (world.getBlockState(pos) != slab) {
            world.setBlockState(pos, slab, Block.NOTIFY_LISTENERS);
        }
        int side = box.getMaxX() - box.getMinX() + 1;
        if (!supportBand(x - box.getMinX(), side) || !supportBand(z - box.getMinZ(), side)) return;
        int bottom = Math.max(world.getBottomY() + 1, floorY - 1 - SUPPORT_MAX_DEPTH);
        BlockState post = style.support();
        for (int y = floorY - 2; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState current = world.getBlockState(pos);
            boolean solid = !current.isAir() && !current.isReplaceable() && current.getFluidState().isEmpty();
            if (solid) break;
            world.setBlockState(pos, post, Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * A one-block skin of rock over the roof of a maze buried in the Overworld, and one course of
     * it down each outer face.
     *
     * <p>A sealed box is only sealed against what touches it. The cherry maze's skin is leaves,
     * logs and planks, and the rule that buries it leaves four blocks of rock over the ceiling at
     * the thinnest point — but a cave can be inside those four blocks, and lava inside the cave.
     * Fire needs an air block next to something that burns; this takes the air away. It also
     * plugs any cave that would otherwise open onto the outer wall and let mobs path in.</p>
     *
     * <p>Only where there is actually something to fill, so the vast majority of columns cost one
     * read and no write. Not in the Nether, where the doorway is <em>meant</em> to open onto a
     * cave — {@code mostOpenSide} picks the side where the rock is most open on purpose.</p>
     */
    private void buildShell(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z) {
        if (style.isNether()) return;
        seal(world, pos, x, floorY + ROOF_DY + 1, z);
    }

    /**
     * One block of rock over anything of ours that burns, where there is a hole for fire to
     * live in.
     *
     * <p>Fire needs an <em>air</em> block beside something flammable; this takes the air away.
     * The maze's own roof and floor have been sealed this way since the cherry style got a
     * wooden ceiling — but the two rooms hung underneath had not, and they are the same problem
     * one layer down: the crypt's floor and the vault's are {@code style.plaza}, which for the
     * cherry style is planks, and both ceilings carry {@code style.pillar} beams, which are
     * logs. Stone bricks on four sides and kindling on the other two is not a sealed room.</p>
     *
     * <p>Only where there is actually something to fill, so a room dug into solid rock — which
     * is nearly all of them — costs one read per column and no writes.</p>
     *
     * <p>"Something to fill" used to mean air, a replaceable block or a fluid. It now means
     * anything that is not already <b>sound rock</b>: an opaque full cube that does not burn and
     * does not fall. The three it adds are worth naming. A pointed dripstone or an amethyst
     * cluster hanging in a cave is none of air, replaceable or fluid, so the old test walked
     * past it and left a see-through gap in a skin whose whole job is to be solid. Leaves at a
     * shallow burial were left as kindling against a wall built to keep fire out. And sand or
     * gravel is a full cube right up until somebody digs under it from the cave next door.</p>
     *
     * <p>What it fills with is {@link #skinAt}, not the style's own stone — see there.</p>
     */
    /**
     * A mark a player left to find their way back: the trail of breadcrumbs.
     *
     * <p>The protection used to refuse every block inside an unconquered maze, which read as
     * strict and was really a hole in the design. A maze is a place you are meant to get lost
     * in, and the oldest answer to getting lost is to leave a mark behind you. Refusing that
     * does not make the maze harder to solve, it makes it tedious to re-walk.</p>
     *
     * <p>Two properties make this list the right one, and both are load-bearing. None of these
     * <b>can be used to cheat</b>: you do not cross a wall on a carpet, you do not climb a
     * banner, a torch holds nothing up. Ladders, scaffolding and solid blocks stay refused,
     * because those let you leave the corridors, which is the one thing a maze is for. And none
     * of them is <b>ever placed by the mod</b> — the styles light themselves with lanterns,
     * glowstone, shroomlight and froglight, and nothing anywhere lays a torch, banner, sign,
     * carpet or candle. So a torch standing in a maze was put there by a player, which is what
     * makes it safe both to let them break it again and, here, to refuse to clear it.</p>
     */
    public static boolean isMarker(BlockState state) {
        return state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH)
                || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH)
                || state.isOf(Blocks.REDSTONE_TORCH) || state.isOf(Blocks.REDSTONE_WALL_TORCH)
                || state.isIn(BlockTags.BANNERS)
                || state.isIn(BlockTags.ALL_SIGNS)
                || state.isIn(BlockTags.WOOL_CARPETS)
                || state.isIn(BlockTags.CANDLES);
    }

    private void seal(StructureWorldAccess world, BlockPos.Mutable pos, int x, int y, int z) {
        pos.set(x, y, z);
        if (isSoundRock(world, pos, world.getBlockState(pos))) return;
        world.setBlockState(pos, skinAt(world, pos, y), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
    }

    /**
     * Is this block already doing the skin's job, so that the skin can leave it alone?
     *
     * <p>Opaque, a full cube, does not burn and does not fall. Live rock that answers yes is the
     * best camouflage there is and it is free, so the skin keeps it and writes nothing.</p>
     *
     * <p>This one predicate is also what makes a rebuild safe, and the reason it is a method
     * rather than two copies of a condition. {@link #seal} skips a block exactly when this is
     * true, and {@link #skinAt} refuses to borrow a neighbour unless this is true of it — so
     * every block the skin writes is one this returns true for, and the next pass over the same
     * column skips it. The loop closes by construction instead of by trusting a list, which
     * matters because a rebuild runs this code over a maze that has already been built once.</p>
     */
    private static boolean isSoundRock(StructureWorldAccess world, BlockPos pos, BlockState state) {
        return state.isOpaqueFullCube(world, pos)
                && !state.isBurnable()
                && !(state.getBlock() instanceof FallingBlock);
    }

    /**
     * Natural blocks the skin is allowed to borrow from the ground it is cut into.
     *
     * <p>Explicit and short on purpose, rather than a tag or a predicate. Every entry is a full,
     * opaque, non-flammable cube that does not fall and carries no block entity, so copying one
     * can never drop an item, start a fire, leave a gap or spill a container into the world.
     * Sand, red sand and gravel are the conspicuous absentees: exactly the right colour for a
     * desert or a sea bed, and exactly the wrong thing to hold a ceiling up with.</p>
     */
    private static final java.util.Set<Block> NATURAL_FILL = java.util.Set.of(
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.CLAY, Blocks.PACKED_MUD,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA,
            Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA,
            Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA,
            Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA,
            Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA,
            Blocks.BLACK_TERRACOTTA,
            Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.NETHERRACK);

    /**
     * What one block of the maze's outer skin is made of.
     *
     * <p>The skin is the underside, the course over the roof and the ring round the sides. None
     * of the three is ever seen from inside the maze — the ring stands outside the maze's own
     * wall, the roof course above its ceiling, the underside below its floor — so what they are
     * made of is a question about the <em>world</em>, and the world's answer is not the style's
     * decorative stone.</p>
     *
     * <p>It was. The badlands lays {@code RED_SANDSTONE}, and buried in rock that is invisible
     * and costs nothing. Opened by a cave, a ravine or a sea bed it is a flawless orange wall
     * seven blocks tall running the length of a hundred and forty-nine, or a flat orange plate
     * on the ocean floor — the one thing in the picture that no terrain in the game contains.
     * The maze was never the problem; the maze's own stone was.</p>
     *
     * <p>So: borrow the first of the six neighbours that is natural ground, which puts terracotta
     * in a badlands plateau, deepslate deep down and plain stone in an ordinary cave, and spreads
     * sideways from the live rock at the edge of the hole into the middle of it. Where nothing
     * natural touches — the middle of a wide opening, or a sea bed whose floor we have already
     * replaced — fall back on what the Overworld is actually made of at that height.</p>
     *
     * <p>The Nether and the End keep their own stone and this does not touch them. Their styles
     * already lay netherrack, basalt, soul soil and end stone — the ground those dimensions are
     * made of — so there is nothing to hide; and a fortress hanging over the lava sea is
     * <em>supposed</em> to read as built.</p>
     *
     * <p>A borrowed neighbour has to pass {@link #isSoundRock} as well as being on the list. The
     * list says what is tasteful, that says what is safe, and the second is what keeps a rebuild
     * from writing over its own work for ever.</p>
     */
    private BlockState skinAt(StructureWorldAccess world, BlockPos pos, int y) {
        if (style.isNether() || style.isEnd()) return style.foundation;
        for (Direction dir : Direction.values()) {
            BlockPos at = pos.offset(dir);
            BlockState neighbour = world.getBlockState(at);
            if (NATURAL_FILL.contains(neighbour.getBlock()) && isSoundRock(world, at, neighbour)) {
                return neighbour;
            }
        }
        return y < 0 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    /**
     * The sides of that skin: the one-block ring around a landmark maze.
     *
     * <p>This is what the flattened ring became once the maze stopped having a doorway. Four
     * blocks of walled corridor going right round a maze nobody can enter from outside was dead
     * space; one block of rock is the wall the hedge needs behind it. From inside, the player
     * sees exactly what they saw before — the maze's own outer wall, at the same place.</p>
     *
     * <p>It wrote {@code style.foundation} over all seven levels of every column, whatever was
     * already there. That is the same mistake the underside made, and worse: the underside at
     * least only showed from below, while this is the face a cave runs alongside. It is now
     * {@link #seal}, which keeps the live rock where there is live rock — the best camouflage
     * there is, and free — and fills the rest with {@link #skinAt}.</p>
     *
     * <p>Sound rock in place of our own stone loses nothing the ring was for. The ring has to be
     * solid, opaque, non-flammable and not about to fall, and that is precisely what
     * {@code seal} now tests for before it leaves a block alone. It also makes a repair
     * idempotent: the old version wrote foundation back over the camouflage every single
     * time.</p>
     */
    private void fillRockRing(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z,
                              BlockPos keep) {
        for (int y = floorY - 1; y <= floorY + ROOF_DY + 1; y++) {
            pos.set(x, y, z);
            if (pos.equals(keep)) continue;
            seal(world, pos, x, y, z);
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
            BlockState standing = world.getBlockState(pos);
            // A breadcrumb a player left in the corridor is not spillage to be cleared. The
            // repair is one-time per chunk, but it runs a couple of chunks a tick off a queue,
            // so a player walking into fresh maze can easily place a torch seconds before that
            // chunk's turn comes up. Without this, their trail would vanish behind them --
            // which is precisely the thing the trail exists to prevent.
            if (isMarker(standing) && target.isAir()) continue;
            if (standing != target) {
                // SKIP_DROPS, as in the open-air pass above: a rebuild must never spill.
                world.setBlockState(pos, target, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
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
