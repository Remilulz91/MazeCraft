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
 *   <li>{@code pillar}    — posts at every wall intersection (also holds the levers: must be a full solid block)</li>
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
