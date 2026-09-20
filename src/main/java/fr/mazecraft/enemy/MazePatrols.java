package fr.mazecraft.enemy;

import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.MazeSize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Patrols: while a survival/adventure player is inside an unconquered maze, a mob of the maze's
 * style appears every N seconds in a corridor 12–24 blocks away, out of the player's line of
 * sight. Works day and night in every dimension (no spawner, nothing to farm): the number of
 * patrol mobs alive around the player is capped, and patrols stop once the maze is conquered.
 */
public final class MazePatrols {

    private static final int CHECK_TICKS = 20;
    private static final int MIN_DIST = 12;
    private static final int MAX_DIST = 24;

    /** Next tick at which each player may receive a patrol mob. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private MazePatrols() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_TICKS != 0) return;
            MazeCraftConfig cfg = MazeCraftConfig.get();
            if (!cfg.enablePatrols || cfg.enemyMultiplier <= 0 || world.getDifficulty() == Difficulty.PEACEFUL) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isCreative() || player.isSpectator()) continue;
                tick(world, player, cfg);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT.clear());
    }

    private static void tick(ServerWorld world, ServerPlayerEntity player, MazeCraftConfig cfg) {
        BlockPos p = player.getBlockPos();
        MazeFinder.MazeHit hit = MazeFinder.find(world, p, 0);
        if (hit == null || MazeState.get(world).isSolved(hit.key())) return;
        MazePiece maze = hit.piece();
        if (!maze.isInsideMaze(p.getX(), p.getZ())) return;

        long now = world.getTime();
        MazeSize size = maze.getSize();
        long interval = Math.max(100, Math.round(cfg.patrolIntervalSeconds * 20 * intervalFactor(size)));
        Long next = NEXT.get(player.getUuid());
        if (next == null) {
            NEXT.put(player.getUuid(), now + interval / 2); // first one comes a bit faster
            return;
        }
        if (now < next) return;
        NEXT.put(player.getUuid(), now + interval);

        int cap = Math.max(1, (int) Math.round(cap(size) * cfg.enemyMultiplier));
        Box around = player.getBoundingBox().expand(40, 8, 40);
        int alive = world.getEntitiesByClass(MobEntity.class, around,
                e -> e.isAlive() && e.getCommandTags().contains(MazeEnemies.PATROL_TAG)).size();
        if (alive >= cap) return;

        Random random = world.getRandom();
        BlockPos spot = hiddenSpot(world, player, maze, random);
        if (spot == null) return;
        List<EntityType<? extends MobEntity>> pool = MazeEnemies.pool(maze.getStyle());
        int tier = size.enemyTier(maze.zoneProgress(spot.getX(), spot.getZ()));
        MobEntity mob = MazeEnemies.spawn(world, pool.get(random.nextInt(pool.size())), spot, tier, false, SpawnReason.EVENT);
        if (mob != null) mob.addCommandTag(MazeEnemies.PATROL_TAG);
    }

    private static double intervalFactor(MazeSize size) {
        return switch (size) { case SMALL -> 1.0; case MEDIUM -> 0.75; case LARGE -> 0.6; case COLOSSAL -> 0.5; };
    }

    private static int cap(MazeSize size) {
        return switch (size) { case SMALL -> 3; case MEDIUM -> 4; case LARGE -> 5; case COLOSSAL -> 6; };
    }

    /** Corridor spot 12–24 blocks away that the player can't see (a wall is in between). */
    private static BlockPos hiddenSpot(ServerWorld world, ServerPlayerEntity player, MazePiece maze, Random random) {
        BlockPos p = player.getBlockPos();
        int y = maze.getFloorY() + 1;
        Vec3d eyes = player.getEyePos();
        for (int attempt = 0; attempt < 40; attempt++) {
            int dx = random.nextBetween(-MAX_DIST, MAX_DIST);
            int dz = random.nextBetween(-MAX_DIST, MAX_DIST);
            int d2 = dx * dx + dz * dz;
            if (d2 < MIN_DIST * MIN_DIST || d2 > MAX_DIST * MAX_DIST) continue;
            BlockPos pos = new BlockPos(p.getX() + dx, y, p.getZ() + dz);
            if (!maze.isCorridor(pos.getX(), pos.getZ())) continue;
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) continue;
            Vec3d target = Vec3d.ofCenter(pos.up());
            HitResult ray = world.raycast(new RaycastContext(eyes, target,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (ray.getType() == HitResult.Type.MISS) continue; // visible: not a good spot
            return pos;
        }
        return null;
    }
}
