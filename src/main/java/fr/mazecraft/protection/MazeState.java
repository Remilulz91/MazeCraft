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
    /** Mazes whose champion has been summoned and not killed yet (saved: survives chunk unloads and restarts). */
    private final LongSet championAlive = new LongOpenHashSet();

    public boolean hasChampion(long mazeKey) {
        return championAlive.contains(mazeKey);
    }

    public void setChampion(long mazeKey, boolean alive) {
        if (alive ? championAlive.add(mazeKey) : championAlive.remove(mazeKey)) markDirty();
    }

    /**
     * Mazes whose vault door has been opened with the right code.
     *
     * <p>Per maze and not per player, like a gate and unlike a step of the ladder: the door is a
     * block, and a block cannot be open for one person and shut for another. Whoever types the
     * code opens it for everybody on the server, which is the same bargain the levers make.</p>
     */
    private final LongSet vaultsOpen = new LongOpenHashSet();

    public boolean isVaultOpen(long mazeKey) {
        return vaultsOpen.contains(mazeKey);
    }

    /** @return true if the vault was still sealed */
    public boolean openVault(long mazeKey) {
        boolean added = vaultsOpen.add(mazeKey);
        if (added) markDirty();
        return added;
    }

    /** DEBUG: seal a vault again (the door itself is not rebuilt). */
    public boolean resealVault(long mazeKey) {
        boolean removed = vaultsOpen.remove(mazeKey);
        if (removed) markDirty();
        return removed;
    }

    /** Chunks already repaired once after full generation (see MazeRepair). */
    private final LongSet repairedChunks = new LongOpenHashSet();

    public boolean isRepaired(long chunk) {
        return repairedChunks.contains(chunk);
    }

    public void markRepaired(long chunk) {
        if (repairedChunks.add(chunk)) markDirty();
    }

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
        setChampion(mazeKey, false);
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
        nbt.putLongArray("Repaired", repairedChunks.toLongArray());
        nbt.putLongArray("Champions", championAlive.toLongArray());
        nbt.putLongArray("VaultsOpen", vaultsOpen.toLongArray());
        return nbt;
    }

    public static MazeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MazeState state = new MazeState();
        for (long key : nbt.getLongArray("Solved")) {
            state.solved.add(key);
        }
        for (long key : nbt.getLongArray("Champions")) {
            state.championAlive.add(key);
        }
        for (long key : nbt.getLongArray("VaultsOpen")) {
            state.vaultsOpen.add(key);
        }
        for (long chunk : nbt.getLongArray("Repaired")) {
            state.repairedChunks.add(chunk);
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
