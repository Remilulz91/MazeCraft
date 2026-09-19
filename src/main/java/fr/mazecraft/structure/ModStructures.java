package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Registers the maze structure type and its piece type.
 * Must run during mod initialization, before datapacks (worldgen JSON) are loaded.
 */
public final class ModStructures {

    public static final StructureType<MazeStructure> MAZE = Registry.register(
            Registries.STRUCTURE_TYPE, MazeCraft.id("maze"),
            (StructureType<MazeStructure>) () -> MazeStructure.CODEC);

    public static final StructurePieceType MAZE_PIECE = Registry.register(
            Registries.STRUCTURE_PIECE, MazeCraft.id("maze_piece"),
            (StructurePieceType) MazePiece::new);

    private ModStructures() { }

    /** Forces class loading (and therefore registration). */
    public static void register() {
        MazeCraft.LOGGER.info("[MazeCraft] Structure types registered");
    }
}
