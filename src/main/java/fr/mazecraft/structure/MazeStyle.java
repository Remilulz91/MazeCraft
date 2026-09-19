package fr.mazecraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * Block palette of a maze. One style per biome family.
 *
 * <ul>
 *   <li>{@code wall}      — wall body</li>
 *   <li>{@code pillar}    — posts at every wall intersection (also holds the levers: must be a full solid block).
 *                           Must NOT be a soil either: its top is open to the sky and trees grow there</li>
 *   <li>{@code wallBase}  — floor block under the walls (hidden)</li>
 *   <li>{@code floor}     — corridor + ring floor. Must NOT be a soil (dirt/grass/sand/moss...), so no tree,
 *                           cactus or plant can grow in or next to the maze</li>
 *   <li>{@code plaza}     — floor of the central room</li>
 *   <li>{@code light}     — placed on top of some pillars</li>
 *   <li>{@code foundation}— fills the gaps under the maze on uneven ground</li>
 *   <li>{@code gate}      — bars closing a gate until its lever is pulled (fence / bars: connected on generation)</li>
 *   <li>{@code margin}    — width of the flattened ring around the maze (wider where trees are big)</li>
 *   <li>{@code ring}      — weighted mix of blocks for the ring floor. Same rule as {@code floor}: no soil
 *                           (no dirt/grass/moss/sand/terracotta), otherwise trees, cacti or dead bushes grow there</li>
 * </ul>
 */
public enum MazeStyle {
    /** Plains, meadows, forests. */
    HEDGE(
            Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true),
            Blocks.OAK_LOG.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.STONE_BRICKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            5,
            Mix.of(Blocks.DIRT_PATH.getDefaultState(), 55,
                    Blocks.GRAVEL.getDefaultState(), 20,
                    Blocks.PACKED_MUD.getDefaultState(), 15,
                    Blocks.MOSSY_COBBLESTONE.getDefaultState(), 10)
    ),
    /** Deserts: carved sandstone, iron bars, terracotta plaza. */
    DESERT(
            Blocks.CUT_SANDSTONE.getDefaultState(),
            Blocks.CHISELED_SANDSTONE.getDefaultState(),
            Blocks.SANDSTONE.getDefaultState(),
            Blocks.SMOOTH_SANDSTONE.getDefaultState(),
            Blocks.ORANGE_TERRACOTTA.getDefaultState(),
            lantern(),
            Blocks.SANDSTONE.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            5,
            Mix.of(Blocks.SMOOTH_SANDSTONE.getDefaultState(), 55,
                    Blocks.SANDSTONE.getDefaultState(), 25,
                    Blocks.CUT_SANDSTONE.getDefaultState(), 15,
                    Blocks.CHISELED_SANDSTONE.getDefaultState(), 5)
    ),
    /** Snowy plains / snowy taiga: packed ice walls, spruce posts, snow floor. */
    SNOW(
            Blocks.PACKED_ICE.getDefaultState(),
            Blocks.SPRUCE_LOG.getDefaultState(),
            Blocks.PACKED_ICE.getDefaultState(),
            Blocks.SNOW_BLOCK.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(),
            lantern(),
            Blocks.SNOW_BLOCK.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            5,
            // Mostly hidden under the natural snow layer, but visible where it melts / gets dug
            Mix.of(Blocks.SNOW_BLOCK.getDefaultState(), 70,
                    Blocks.PACKED_ICE.getDefaultState(), 20,
                    Blocks.SPRUCE_PLANKS.getDefaultState(), 10)
    ),
    /** Jungles: mossy ruins, jungle posts, bamboo gates. Wider ring: jungle trees are huge. */
    JUNGLE(
            Blocks.MOSSY_COBBLESTONE.getDefaultState(),
            Blocks.JUNGLE_LOG.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.CHISELED_STONE_BRICKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.BAMBOO_FENCE.getDefaultState(),
            8,
            Mix.of(Blocks.MOSSY_STONE_BRICKS.getDefaultState(), 35,
                    Blocks.CRACKED_STONE_BRICKS.getDefaultState(), 20,
                    Blocks.STONE_BRICKS.getDefaultState(), 15,
                    Blocks.MOSSY_COBBLESTONE.getDefaultState(), 15,
                    Blocks.COBBLESTONE.getDefaultState(), 10,
                    Blocks.ANDESITE.getDefaultState(), 5)
    ),
    /** Badlands: striped terracotta walls (bands by height), red sandstone floor. */
    BADLANDS(
            Blocks.TERRACOTTA.getDefaultState(),
            Blocks.CHISELED_RED_SANDSTONE.getDefaultState(),
            Blocks.RED_SANDSTONE.getDefaultState(),
            Blocks.SMOOTH_RED_SANDSTONE.getDefaultState(),
            Blocks.CUT_RED_SANDSTONE.getDefaultState(),
            lantern(),
            Blocks.RED_SANDSTONE.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            5,
            // No terracotta / red sand in the ring: dead bushes grow on them
            Mix.of(Blocks.SMOOTH_RED_SANDSTONE.getDefaultState(), 50,
                    Blocks.RED_SANDSTONE.getDefaultState(), 25,
                    Blocks.CUT_RED_SANDSTONE.getDefaultState(), 20,
                    Blocks.CHISELED_RED_SANDSTONE.getDefaultState(), 5),
            new BlockState[]{
                    Blocks.RED_TERRACOTTA.getDefaultState(),
                    Blocks.ORANGE_TERRACOTTA.getDefaultState(),
                    Blocks.WHITE_TERRACOTTA.getDefaultState(),
                    // Top band: cut red sandstone coping, NOT terracotta (dead bushes grow on terracotta)
                    Blocks.CUT_RED_SANDSTONE.getDefaultState()
            }
    ),
    /** Dark forest: dark oak hedges, soul lanterns. Wide ring: dark oaks are large and dense. */
    DARK_FOREST(
            leaves(Blocks.DARK_OAK_LEAVES),
            Blocks.DARK_OAK_LOG.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.DARK_OAK_PLANKS.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, false),
            Blocks.DIRT.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            8,
            Mix.of(Blocks.DIRT_PATH.getDefaultState(), 40,
                    Blocks.PACKED_MUD.getDefaultState(), 25,
                    Blocks.MOSSY_COBBLESTONE.getDefaultState(), 20,
                    Blocks.GRAVEL.getDefaultState(), 15)
    ),
    /** Cherry grove: pink hedges, cherry wood. */
    CHERRY(
            leaves(Blocks.CHERRY_LEAVES),
            Blocks.CHERRY_LOG.getDefaultState(),
            Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.CHERRY_PLANKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.CHERRY_FENCE.getDefaultState(),
            6,
            Mix.of(Blocks.DIRT_PATH.getDefaultState(), 50,
                    Blocks.GRAVEL.getDefaultState(), 15,
                    Blocks.PACKED_MUD.getDefaultState(), 15,
                    Blocks.STONE_BRICKS.getDefaultState(), 10,
                    Blocks.CRACKED_STONE_BRICKS.getDefaultState(), 10)
    ),
    /** Swamp / mangrove: mangrove hedges, muddy roots, mud floor. Wide ring: mangroves spread. */
    SWAMP(
            leaves(Blocks.MANGROVE_LEAVES),
            // NOT muddy mangrove roots: they count as soil (#minecraft:dirt), so mangrove trees
            // grew on top of the posts
            Blocks.MANGROVE_LOG.getDefaultState(),
            Blocks.MUD_BRICKS.getDefaultState(),
            Blocks.PACKED_MUD.getDefaultState(),
            Blocks.MANGROVE_PLANKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.MANGROVE_FENCE.getDefaultState(),
            8,
            Mix.of(Blocks.PACKED_MUD.getDefaultState(), 45,
                    Blocks.MUD_BRICKS.getDefaultState(), 25,
                    Blocks.MOSSY_COBBLESTONE.getDefaultState(), 10,
                    Blocks.GRAVEL.getDefaultState(), 10,
                    Blocks.MANGROVE_PLANKS.getDefaultState(), 10)
    ),
    /** Savanna: acacia hedges. Wide ring: acacias lean and spread far from their trunk. */
    SAVANNA(
            leaves(Blocks.ACACIA_LEAVES),
            Blocks.ACACIA_LOG.getDefaultState(),
            Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.ACACIA_PLANKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            8,
            Mix.of(Blocks.DIRT_PATH.getDefaultState(), 45,
                    Blocks.PACKED_MUD.getDefaultState(), 20,
                    Blocks.GRAVEL.getDefaultState(), 15,
                    Blocks.SMOOTH_SANDSTONE.getDefaultState(), 10,
                    Blocks.COBBLESTONE.getDefaultState(), 10)
    ),
    /** Taiga: spruce hedges. Wide ring: old-growth taigas have giant spruces. */
    TAIGA(
            leaves(Blocks.SPRUCE_LEAVES),
            Blocks.SPRUCE_LOG.getDefaultState(),
            Blocks.MOSSY_COBBLESTONE.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(),
            lantern(),
            Blocks.DIRT.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            8,
            Mix.of(Blocks.DIRT_PATH.getDefaultState(), 40,
                    Blocks.GRAVEL.getDefaultState(), 20,
                    Blocks.MOSSY_COBBLESTONE.getDefaultState(), 15,
                    Blocks.COBBLESTONE.getDefaultState(), 15,
                    Blocks.PACKED_MUD.getDefaultState(), 10)
    ),

    // =====================================================================
    // NETHER — enclosed mazes carved into the rock: sealed outer wall, roof with lights
    // =====================================================================

    /** Nether wastes: fortress-like nether bricks. */
    FORTRESS(
            Blocks.NETHER_BRICKS.getDefaultState(),
            Blocks.RED_NETHER_BRICKS.getDefaultState(),
            Blocks.NETHER_BRICKS.getDefaultState(),
            Blocks.CRACKED_NETHER_BRICKS.getDefaultState(),
            Blocks.CHISELED_NETHER_BRICKS.getDefaultState(),
            Blocks.NETHERRACK.getDefaultState(),
            Blocks.NETHER_BRICK_FENCE.getDefaultState(),
            Mix.of(Blocks.CRACKED_NETHER_BRICKS.getDefaultState(), 40,
                    Blocks.NETHER_BRICKS.getDefaultState(), 30,
                    Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 20,
                    Blocks.RED_NETHER_BRICKS.getDefaultState(), 10),
            Blocks.NETHER_BRICKS.getDefaultState(),
            Blocks.GLOWSTONE.getDefaultState(),
            null
    ),
    /** Crimson forest: nether wart walls, crimson wood, shroomlights. */
    CRIMSON(
            Blocks.NETHER_WART_BLOCK.getDefaultState(),
            Blocks.CRIMSON_STEM.getDefaultState(),
            Blocks.CRIMSON_PLANKS.getDefaultState(),
            Blocks.CRIMSON_PLANKS.getDefaultState(),
            Blocks.RED_NETHER_BRICKS.getDefaultState(),
            Blocks.NETHERRACK.getDefaultState(),
            Blocks.CRIMSON_FENCE.getDefaultState(),
            Mix.of(Blocks.CRIMSON_PLANKS.getDefaultState(), 45,
                    Blocks.CRIMSON_HYPHAE.getDefaultState(), 20,
                    Blocks.RED_NETHER_BRICKS.getDefaultState(), 20,
                    Blocks.NETHER_BRICKS.getDefaultState(), 15),
            // Roof NOT netherrack / nether wart: weeping vines would hang from it into the corridors
            Blocks.CRIMSON_PLANKS.getDefaultState(),
            Blocks.SHROOMLIGHT.getDefaultState(),
            null
    ),
    /** Warped forest: warped wart walls, warped wood, shroomlights. */
    WARPED(
            Blocks.WARPED_WART_BLOCK.getDefaultState(),
            Blocks.WARPED_STEM.getDefaultState(),
            Blocks.WARPED_PLANKS.getDefaultState(),
            Blocks.WARPED_PLANKS.getDefaultState(),
            Blocks.WARPED_HYPHAE.getDefaultState(),
            Blocks.NETHERRACK.getDefaultState(),
            Blocks.WARPED_FENCE.getDefaultState(),
            Mix.of(Blocks.WARPED_PLANKS.getDefaultState(), 45,
                    Blocks.WARPED_HYPHAE.getDefaultState(), 20,
                    Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 20,
                    Blocks.NETHER_BRICKS.getDefaultState(), 15),
            Blocks.WARPED_PLANKS.getDefaultState(),
            Blocks.SHROOMLIGHT.getDefaultState(),
            null
    ),
    /** Soul sand valley: bone walls, blackstone, soul soil floor, hanging soul lanterns. */
    SOUL(
            Blocks.BONE_BLOCK.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.SOUL_SOIL.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.SOUL_SOIL.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Mix.of(Blocks.SOUL_SOIL.getDefaultState(), 45,
                    Blocks.POLISHED_BLACKSTONE.getDefaultState(), 25,
                    Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 20,
                    Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 10),
            // Roof NOT blackstone / basalt / netherrack: glowstone blobs would grow under it
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true)
    ),
    /** Basalt deltas: polished basalt walls, blackstone, gilded plaza. */
    BASALT(
            Blocks.POLISHED_BASALT.getDefaultState(),
            Blocks.BLACKSTONE.getDefaultState(),
            Blocks.BASALT.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.GILDED_BLACKSTONE.getDefaultState(),
            Blocks.BASALT.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Mix.of(Blocks.POLISHED_BLACKSTONE.getDefaultState(), 40,
                    Blocks.POLISHED_BASALT.getDefaultState(), 20,
                    Blocks.SMOOTH_BASALT.getDefaultState(), 20,
                    Blocks.BLACKSTONE.getDefaultState(), 20),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.GLOWSTONE.getDefaultState(),
            null
    );

    public final BlockState wall;
    public final BlockState pillar;
    public final BlockState wallBase;
    public final BlockState floor;
    public final BlockState plaza;
    public final BlockState light;
    public final BlockState foundation;
    public final BlockState gate;
    public final int margin;
    public final Mix ring;
    /** Enclosed maze (Nether): sealed outer wall + roof, no sky. */
    public final boolean enclosed;
    /** Roof block (enclosed mazes only). */
    public final BlockState ceiling;
    /** Light block set into the roof (enclosed mazes only). */
    public final BlockState ceilingLight;
    /** Optional lantern hanging under the roof instead of a light block in it (enclosed mazes only). */
    public final BlockState hangingLight;
    /** Loot table prefix: chests/<prefix>_<size>. */
    public final String lootPrefix;

    /** Optional horizontal bands: wall block per height above the floor (1 = lowest). Null = plain {@link #wall}. */
    private final BlockState[] wallBands;

    MazeStyle(BlockState wall, BlockState pillar, BlockState wallBase, BlockState floor,
              BlockState plaza, BlockState light, BlockState foundation, BlockState gate, int margin, Mix ring) {
        this.wall = wall;
        this.pillar = pillar;
        this.wallBase = wallBase;
        this.floor = floor;
        this.plaza = plaza;
        this.light = light;
        this.foundation = foundation;
        this.gate = gate;
        this.margin = margin;
        this.ring = ring;
        this.wallBands = null;
        this.enclosed = false;
        this.ceiling = null;
        this.ceilingLight = null;
        this.hangingLight = null;
        this.lootPrefix = "maze";
    }

    MazeStyle(BlockState wall, BlockState pillar, BlockState wallBase, BlockState floor,
              BlockState plaza, BlockState light, BlockState foundation, BlockState gate, int margin, Mix ring,
              BlockState[] wallBands) {
        this.wall = wall;
        this.pillar = pillar;
        this.wallBase = wallBase;
        this.floor = floor;
        this.plaza = plaza;
        this.light = light;
        this.foundation = foundation;
        this.gate = gate;
        this.margin = margin;
        this.ring = ring;
        this.wallBands = wallBands;
        this.enclosed = false;
        this.ceiling = null;
        this.ceilingLight = null;
        this.hangingLight = null;
        this.lootPrefix = "maze";
    }

    /** Enclosed (Nether) style. */
    MazeStyle(BlockState wall, BlockState pillar, BlockState wallBase, BlockState floor, BlockState plaza,
              BlockState foundation, BlockState gate, Mix ring,
              BlockState ceiling, BlockState ceilingLight, BlockState hangingLight) {
        this.wall = wall;
        this.pillar = pillar;
        this.wallBase = wallBase;
        this.floor = floor;
        this.plaza = plaza;
        this.light = ceilingLight;
        this.foundation = foundation;
        this.gate = gate;
        this.margin = 4;
        this.ring = ring;
        this.wallBands = null;
        this.enclosed = true;
        this.ceiling = ceiling;
        this.ceilingLight = ceilingLight;
        this.hangingLight = hangingLight;
        this.lootPrefix = "nether_maze";
    }

    /** Wall block at {@code dy} blocks above the floor (1..WALL_HEIGHT). */
    public BlockState wallAt(int dy) {
        return wallBands == null ? wall : wallBands[(dy - 1) % wallBands.length];
    }

    private static BlockState leaves(net.minecraft.block.Block block) {
        return block.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState lantern() {
        return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
    }

    /**
     * Weighted block mix. The block at (x, z) is chosen from a hash of the position, so it is
     * the same in every chunk and on every reload (no randomness to keep in sync).
     */
    public static final class Mix {
        private final BlockState[] states;
        private final int[] weights;
        private final int total;

        private Mix(BlockState[] states, int[] weights) {
            this.states = states;
            this.weights = weights;
            int t = 0;
            for (int w : weights) t += w;
            this.total = t;
        }

        /** Pairs of (BlockState, weight). */
        public static Mix of(Object... pairs) {
            BlockState[] states = new BlockState[pairs.length / 2];
            int[] weights = new int[pairs.length / 2];
            for (int i = 0; i < states.length; i++) {
                states[i] = (BlockState) pairs[2 * i];
                weights[i] = (Integer) pairs[2 * i + 1];
            }
            return new Mix(states, weights);
        }

        public BlockState pick(int x, int z) {
            long hash = MathHelper.hashCode(x, 0, z);
            int r = (int) Math.floorMod(hash >>> 16, (long) total);
            for (int i = 0; i < states.length; i++) {
                r -= weights[i];
                if (r < 0) return states[i];
            }
            return states[0];
        }
    }

    /** Widest ring of all styles (used to budget the structure reach). */
    public static final int MAX_MARGIN = 8;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MazeStyle fromId(String id) {
        for (MazeStyle s : values()) {
            if (s.id().equalsIgnoreCase(id)) return s;
        }
        return null;
    }
}
