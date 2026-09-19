package fr.mazecraft.protection;

import fr.mazecraft.structure.MazeFinder;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * One-time clean-up of every maze chunk, the first time it is fully loaded.
 *
 * Why: a chunk is decorated (trees, basalt columns, lava deltas, vines...) before its
 * neighbours, and each neighbour's decorations may spill into it afterwards. By the time a
 * chunk is fully loaded, all of its neighbours are decorated, so rebuilding the maze in it
 * once removes every leftover for good. Opened gates, the chest and snow layers are kept;
 * conquered mazes are never touched.
 *
 * Chunk loading only queues the work; it is done a few chunks per tick afterwards (changing
 * blocks from inside the chunk-load callback itself is not safe).
 */
public final class MazeRepair {

    /** Queued chunks looked at per tick (cheap: most chunks have no maze). */
    private static final int CHECKS_PER_TICK = 64;
    /** Actual maze repairs per tick (a repair rewrites up to 16×16×20 blocks). */
    private static final int REPAIRS_PER_TICK = 2;

    private static final Map<RegistryKey<World>, LongLinkedOpenHashSet> QUEUES = new HashMap<>();

    private MazeRepair() { }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                QUEUES.computeIfAbsent(world.getRegistryKey(), k -> new LongLinkedOpenHashSet())
                        .add(chunk.getPos().toLong()));

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            LongLinkedOpenHashSet queue = QUEUES.get(world.getRegistryKey());
            if (queue == null || queue.isEmpty()) return;
            MazeState state = MazeState.get(world);
            int repairs = 0;
            for (int n = 0; n < CHECKS_PER_TICK && repairs < REPAIRS_PER_TICK && !queue.isEmpty(); n++) {
                long key = queue.removeFirstLong();
                ChunkPos pos = new ChunkPos(key);
                if (state.isRepaired(key) || !world.isChunkLoaded(pos.x, pos.z)) continue;
                if (repairChunk(world, pos, state)) repairs++;
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUES.clear());
    }

    /** @return true if the chunk contained a maze (and was repaired / marked). */
    private static boolean repairChunk(ServerWorld world, ChunkPos pos, MazeState state) {
        boolean any = false;
        for (MazeFinder.MazeHit hit : MazeFinder.findInChunk(world, pos)) {
            any = true;
            if (state.isSolved(hit.key())) continue;
            long mazeKey = hit.key();
            hit.piece().repair(world, pos, gate -> state.isGateOpen(mazeKey, gate));
        }
        if (any) state.markRepaired(pos.toLong());
        return any;
    }
}
