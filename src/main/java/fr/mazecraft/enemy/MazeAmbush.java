package fr.mazecraft.enemy;

import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.structure.MazePiece;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;

import java.util.List;

/**
 * Lever ambushes: pulling lever i spawns a wave in the corridors around the player
 * (2 mobs on the first lever, up to 6 on the last one), plus a champion on the last lever.
 */
public final class MazeAmbush {

    private static final int MIN_DIST = 6;
    private static final int MAX_DIST = 14;

    private MazeAmbush() { }

    public static void trigger(ServerWorld world, ServerPlayerEntity player, MazePiece maze, int gate, long mazeKey) {
        MazeCraftConfig cfg = MazeCraftConfig.get();
        if (!cfg.enableAmbushes || world.getDifficulty() == Difficulty.PEACEFUL) return;

        int gates = maze.gateCount();
        boolean last = gate == gates - 1;
        double progress = gates <= 1 ? 1.0 : (double) gate / (gates - 1);
        int count = (int) Math.round(maze.getSize().ambushCount(progress) * cfg.enemyMultiplier);
        // At least leather: the helmet keeps zombies / skeletons from burning in daylight
        int tier = Math.max(1, maze.getSize().enemyTier(progress));

        List<EntityType<? extends MobEntity>> pool = MazeEnemies.pool(maze.getStyle());
        Random random = world.getRandom();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = findSpot(world, player, maze, random);
            if (pos == null) continue;
            MobEntity mob = MazeEnemies.spawn(world, pool.get(random.nextInt(pool.size())), pos, tier, false, SpawnReason.EVENT);
            if (mob != null) {
                mob.setTarget(player);
                poof(world, pos);
                spawned++;
            }
        }

        if (last && cfg.enableChampion) {
            BlockPos pos = plazaSpot(world, maze);
            if (pos != null) {
                MobEntity champion = MazeEnemies.spawn(world, MazeEnemies.championType(maze.getStyle()), pos, 0, true, SpawnReason.EVENT);
                if (champion != null) {
                    MazeEnemies.makeChampion(world, champion, maze.getSize());
                    // Saved in the maze state: the chest stays locked until this champion dies,
                    // even if it wanders into unloaded chunks or the server restarts
                    champion.addCommandTag(MazeEnemies.MAZE_TAG_PREFIX + mazeKey);
                    fr.mazecraft.protection.MazeState.get(world).setChampion(mazeKey, true);
                    ChampionTracker.track(world, champion);
                    champion.setTarget(player);
                    poof(world, pos);
                    spawned++;
                    player.sendMessage(Text.translatable("mazecraft.champion.appears").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                }
            }
        }

        if (spawned > 0) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.0f, 0.8f);
            player.sendMessage(Text.translatable("mazecraft.ambush").formatted(Formatting.RED), true);
        }
    }

    /** Random free corridor spot 6–14 blocks from the player, inside the maze, not in the plaza. */
    private static BlockPos findSpot(ServerWorld world, ServerPlayerEntity player, MazePiece maze, Random random) {
        BlockPos p = player.getBlockPos();
        int y = maze.getFloorY() + 1;
        for (int attempt = 0; attempt < 40; attempt++) {
            int dx = random.nextBetween(-MAX_DIST, MAX_DIST);
            int dz = random.nextBetween(-MAX_DIST, MAX_DIST);
            if (dx * dx + dz * dz < MIN_DIST * MIN_DIST) continue;
            BlockPos pos = new BlockPos(p.getX() + dx, y, p.getZ() + dz);
            if (!maze.isCorridor(pos.getX(), pos.getZ())) continue;
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) continue;
            return pos;
        }
        return null;
    }

    /** A free spot on the central plaza, a few blocks from the chest (the champion guards it). */
    private static BlockPos plazaSpot(ServerWorld world, MazePiece maze) {
        BlockPos chest = maze.chestPos();
        int[][] offsets = {{0, -3}, {0, 3}, {-3, 0}, {3, 0}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        for (int[] o : offsets) {
            BlockPos pos = chest.add(o[0], 0, o[1]);
            if (world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir()) return pos;
        }
        return chest.up();
    }

    private static void poof(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                20, 0.3, 0.6, 0.3, 0.02);
    }
}
