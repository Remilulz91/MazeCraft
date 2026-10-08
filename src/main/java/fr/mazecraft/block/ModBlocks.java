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

    /**
     * One key of a vault keypad. Unbreakable like the gateway and with no block item: these are
     * part of a structure, not something to carry home. Twelve faces, one block.
     */
    public static final Block CODE_KEY = Registry.register(Registries.BLOCK, MazeCraft.id("code_key"),
            new CodeKeyBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.STONE_GRAY)
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .requiresTool()
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .sounds(BlockSoundGroup.DEEPSLATE_TILES)));

    /**
     * The door a keypad opens. Unbreakable, like everything else that is a lock rather than a
     * wall: a four-digit code whose other solution is a pickaxe is not a puzzle.
     *
     * <p>Not a {@link SealedGatewayBlock}: that one has no collision on purpose, because who may
     * pass through a maze entrance is decided per player. This door is the same for everybody and
     * has to actually be shut, so it is a plain solid block that is taken away when the code is
     * right.</p>
     */
    public static final Block VAULT_DOOR = Registry.register(Registries.BLOCK, MazeCraft.id("vault_door"),
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.TERRACOTTA_CYAN)
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .requiresTool()
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .sounds(BlockSoundGroup.COPPER)));

    private ModBlocks() { }

    /** Forces class loading so the static registrations above run. */
    public static void register() {
        MazeCraft.LOGGER.info("[MazeCraft] Blocks registered");
    }
}
