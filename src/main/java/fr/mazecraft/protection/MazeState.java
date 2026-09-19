package fr.mazecraft.protection;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/**
 * Per-dimension saved data: which mazes have been conquered (central chest opened) and
 * which gates of each maze have been opened (bitmask, bit i = gate i).
 * Saved in {@code <world>/<dimension>/data/mazecraft_mazes.dat}.
 * A maze is identified by its structure start chunk.
 */
public class MazeState extends PersistentState {

    private static final String STATE_KEY = "mazecraft_mazes";

    private final LongSet solved = new LongOpenHashSet();
    private final Long2IntOpenHashMap openedGates = new Long2IntOpenHashMap();

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

    public boolean isGateOpen(long mazeKey, int gate) {
        return (openedGates.get(mazeKey) & (1 << gate)) != 0;
    }

    /** @return true if the gate was closed before */
    public boolean openGate(long mazeKey, int gate) {
        int mask = openedGates.get(mazeKey);
        if ((mask & (1 << gate)) != 0) return false;
        openedGates.put(mazeKey, mask | (1 << gate));
        markDirty();
        return true;
    }

    /** DEBUG: mark every gate of a maze as closed again (blocks are not rebuilt). */
    public void resetGates(long mazeKey) {
        if (openedGates.remove(mazeKey) != 0) markDirty();
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
        NbtCompound gates = new NbtCompound();
        for (Long2IntMap.Entry e : openedGates.long2IntEntrySet()) {
            gates.putInt(Long.toString(e.getLongKey()), e.getIntValue());
        }
        nbt.put("Gates", gates);
        return nbt;
    }

    public static MazeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MazeState state = new MazeState();
        for (long key : nbt.getLongArray("Solved")) {
            state.solved.add(key);
        }
        NbtCompound gates = nbt.getCompound("Gates");
        for (String k : gates.getKeys()) {
            try {
                state.openedGates.put(Long.parseLong(k), gates.getInt(k));
            } catch (NumberFormatException ignored) { }
        }
        return state;
    }
}
