package fr.mazecraft.protection;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/**
 * Per-dimension saved data: which mazes have been conquered (central chest opened).
 * Saved in {@code <world>/<dimension>/data/mazecraft_mazes.dat}.
 * A maze is identified by its structure start chunk.
 */
public class MazeState extends PersistentState {

    private static final String STATE_KEY = "mazecraft_mazes";

    private final LongSet solved = new LongOpenHashSet();

    private static final PersistentState.Type<MazeState> TYPE = new PersistentState.Type<>(
            MazeState::new,
            MazeState::fromNbt,
            DataFixTypes.LEVEL // closest existing type; harmless for our custom data
    );

    public static MazeState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
    }

    public boolean isSolved(long mazeKey) {
        return solved.contains(mazeKey);
    }

    /** @return true if the maze was not solved before */
    public boolean markSolved(long mazeKey) {
        boolean added = solved.add(mazeKey);
        if (added) markDirty();
        return added;
    }

    /** DEBUG: forget that a maze was solved. */
    public boolean reset(long mazeKey) {
        boolean removed = solved.remove(mazeKey);
        if (removed) markDirty();
        return removed;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putLongArray("Solved", solved.toLongArray());
        return nbt;
    }

    public static MazeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MazeState state = new MazeState();
        for (long key : nbt.getLongArray("Solved")) {
            state.solved.add(key);
        }
        return state;
    }
}
