package fr.mazecraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;

import java.util.Locale;

/**
 * Block palette of a maze. One style per biome family; v0.1 ships the hedge maze only.
 *
 * <ul>
 *   <li>{@code wall}      — wall body</li>
 *   <li>{@code pillar}    — posts at every wall intersection</li>
 *   <li>{@code wallBase}  — floor block under the walls (hidden)</li>
 *   <li>{@code floor}     — corridor floor (NOT in #minecraft:dirt, so no tree/flower grows in the corridors)</li>
 *   <li>{@code plaza}     — floor of the central room</li>
 *   <li>{@code light}     — placed on top of some pillars</li>
 *   <li>{@code foundation}— fills the gaps under the maze on uneven ground</li>
 *   <li>{@code gate}      — bars closing a gate until its lever is pulled (fence / bars: connected on generation)</li>
 * </ul>
 */
public enum MazeStyle {
    HEDGE(
            Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true),
            Blocks.OAK_LOG.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false),
            Blocks.DIRT.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState()
    );

    public final BlockState wall;
    public final BlockState pillar;
    public final BlockState wallBase;
    public final BlockState floor;
    public final BlockState plaza;
    public final BlockState light;
    public final BlockState foundation;
    public final BlockState gate;

    MazeStyle(BlockState wall, BlockState pillar, BlockState wallBase, BlockState floor,
              BlockState plaza, BlockState light, BlockState foundation, BlockState gate) {
        this.wall = wall;
        this.pillar = pillar;
        this.wallBase = wallBase;
        this.floor = floor;
        this.plaza = plaza;
        this.light = light;
        this.foundation = foundation;
        this.gate = gate;
    }

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
