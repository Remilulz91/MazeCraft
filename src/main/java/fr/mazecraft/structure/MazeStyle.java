package fr.mazecraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;

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
            5
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
            5
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
            5
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
            8
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

    MazeStyle(BlockState wall, BlockState pillar, BlockState wallBase, BlockState floor,
              BlockState plaza, BlockState light, BlockState foundation, BlockState gate, int margin) {
        this.wall = wall;
        this.pillar = pillar;
        this.wallBase = wallBase;
        this.floor = floor;
        this.plaza = plaza;
        this.light = light;
        this.foundation = foundation;
        this.gate = gate;
        this.margin = margin;
    }

    private static BlockState lantern() {
        return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
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
