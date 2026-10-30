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
     * Every landmark: the building, the ground levelled under it, and the shaft down.
     *
     * <p>One type for all of them, not one per building. That is not economy — {@code MazeRepair}
     * finds the piece to repair by its class, so a building with a piece type of its own would
     * go unrepaired, and an unrepaired landmark piece is precisely the bug that deleted the
     * pagoda's staircase. A new landmark now touches {@code Landmarks.of} and nothing here.</p>
     */
    public static final StructurePieceType LANDMARK_PIECE = Registry.register(
            Registries.STRUCTURE_PIECE, MazeCraft.id("landmark_piece"),
            (StructurePieceType) fr.mazecraft.landmark.LandmarkPiece::new);

    /**
     * What {@link #LANDMARK_PIECE} used to be called, kept so that a world generated before the
     * rename still loads.
     *
     * <p>A piece whose type id is not in the registry is not skipped, it fails the chunk — so
     * dropping this line would make every saved pagoda a load error rather than a lost feature.
     * Pieces read through it have no {@code Style} tag, and the only landmark that existed then
     * was the pagoda, which is what {@code LandmarkPiece} assumes when the tag is missing.</p>
     */
    public static final StructurePieceType PAGODA_PIECE = Registry.register(
            Registries.STRUCTURE_PIECE, MazeCraft.id("pagoda_piece"),
            (StructurePieceType) (context, nbt) ->
                    new fr.mazecraft.landmark.LandmarkPiece(
                            // Qualified, because a simple name here would be an illegal
                            // forward reference; the lambda runs long after the field is set.
                            ModStructures.PAGODA_PIECE, context, nbt));

    private ModStructures() { }

    /** Forces class loading (and therefore registration). */
    public static void register() {
        MazeCraft.LOGGER.info("[MazeCraft] Structure types registered");
    }
}
