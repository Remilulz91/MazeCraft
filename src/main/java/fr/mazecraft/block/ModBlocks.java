package fr.mazecraft.block;

import fr.mazecraft.MazeCraft;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {

    /**
     * The sealed gateway closing a maze entrance. Indestructible on purpose: it is not a puzzle
     * to be mined around, and it stays even after the maze is conquered because it still gates
     * the other players on the server. It has no block item — it is never obtainable.
     */
    public static final Block SEALED_GATEWAY = Registry.register(Registries.BLOCK, MazeCraft.id("sealed_gateway"),
            new SealedGatewayBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.PURPLE)
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .luminance(state -> 7)
                    .nonOpaque()
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)));

    private ModBlocks() { }

    /** Forces class loading so the static registrations above run. */
    public static void register() {
        MazeCraft.LOGGER.info("[MazeCraft] Blocks registered");
    }
}
