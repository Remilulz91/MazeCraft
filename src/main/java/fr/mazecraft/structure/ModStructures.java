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

    /** The way down to Kronos: surface ruin plus the shaft under it. */
    public static final StructurePieceType KRONOS_GATE_PIECE = Registry.register(
            Registries.STRUCTURE_PIECE, MazeCraft.id("kronos_gate_piece"),
            (StructurePieceType) KronosGatePiece::new);

    /**
     * The cherry pagoda: the building, the ground levelled under it, and the shaft down.
     *
     * <p>This line and {@code Landmarks.of} are the only two places a new landmark has to be
     * mentioned. Registration has to happen somewhere central — a piece type that is not in the
     * registry cannot be written to a save file — so two files it is, rather than one.</p>
     */
    public static final StructurePieceType PAGODA_PIECE = Registry.register(
            Registries.STRUCTURE_PIECE, MazeCraft.id("pagoda_piece"),
            (StructurePieceType) fr.mazecraft.landmark.PagodaPiece::new);

    private ModStructures() { }

    /** Forces class loading (and therefore registration). */
    public static void register() {
        MazeCraft.LOGGER.info("[MazeCraft] Structure types registered");
    }
}
