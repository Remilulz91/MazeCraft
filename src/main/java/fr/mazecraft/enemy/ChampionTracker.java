package fr.mazecraft.enemy;

import fr.mazecraft.MazeCraft;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boss bar of maze champions (shown to players within 48 blocks) and the
 * "defeat a champion" advancement. Champions reloaded from disk get their bar back.
 */
public final class ChampionTracker {

    private static final double BAR_RANGE_SQ = 48 * 48;

    private record Tracked(ServerWorld world, ServerBossBar bar) { }

    private static final Map<UUID, Tracked> CHAMPIONS = new HashMap<>();

    private ChampionTracker() { }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living && living.getCommandTags().contains(MazeEnemies.CHAMPION_TAG)) {
                track(world, living);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CHAMPIONS.isEmpty() || server.getTicks() % 5 != 0) return;
            for (UUID id : new ArrayList<>(CHAMPIONS.keySet())) {
                Tracked t = CHAMPIONS.get(id);
                Entity entity = t.world().getEntity(id);
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                    t.bar().clearPlayers();
                    CHAMPIONS.remove(id);
                    continue;
                }
                t.bar().setPercent(living.getHealth() / living.getMaxHealth());
                for (ServerPlayerEntity player : new ArrayList<>(t.bar().getPlayers())) {
                    if (player.getWorld() != t.world() || player.squaredDistanceTo(living) > BAR_RANGE_SQ) {
                        t.bar().removePlayer(player);
                    }
                }
                for (ServerPlayerEntity player : t.world().getPlayers()) {
                    if (player.squaredDistanceTo(living) <= BAR_RANGE_SQ) t.bar().addPlayer(player);
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getCommandTags().contains(MazeEnemies.CHAMPION_TAG)) return;
            // Unlock the chest of its maze
            if (entity.getWorld() instanceof ServerWorld world) {
                for (String tag : entity.getCommandTags()) {
                    if (!tag.startsWith(MazeEnemies.MAZE_TAG_PREFIX)) continue;
                    try {
                        long key = Long.parseLong(tag.substring(MazeEnemies.MAZE_TAG_PREFIX.length()));
                        fr.mazecraft.protection.MazeState.get(world).setChampion(key, false);
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (source.getAttacker() instanceof ServerPlayerEntity player) {
                AdvancementEntry adv = player.getServer().getAdvancementLoader().get(MazeCraft.id("defeat_champion"));
                if (adv != null) {
                    for (String c : player.getAdvancementTracker().getProgress(adv).getUnobtainedCriteria()) {
                        player.getAdvancementTracker().grantCriterion(adv, c);
                    }
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CHAMPIONS.values().forEach(t -> t.bar().clearPlayers());
            CHAMPIONS.clear();
        });
    }

    public static void track(ServerWorld world, LivingEntity champion) {
        if (CHAMPIONS.containsKey(champion.getUuid())) return;
        ServerBossBar bar = new ServerBossBar(
                Text.translatable("mazecraft.champion.name").formatted(Formatting.GOLD),
                BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        CHAMPIONS.put(champion.getUuid(), new Tracked(world, bar));
    }
}
