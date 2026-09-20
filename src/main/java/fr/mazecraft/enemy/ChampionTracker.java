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

    private record Tracked(ServerWorld world, ServerBossBar bar, boolean minotaur) { }

    /** Minotaur music ("5", ~178 s) is replayed after this many ticks while the player stays near. */
    private static final int MUSIC_LENGTH_TICKS = 180 * 20;
    /** Player UUID → tick at which the Minotaur music was last started for them. */
    private static final Map<UUID, Integer> MUSIC_STARTED = new HashMap<>();

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
                    if (t.minotaur()) t.bar().getPlayers().forEach(ChampionTracker::stopMusic);
                    t.bar().clearPlayers();
                    CHAMPIONS.remove(id);
                    continue;
                }
                t.bar().setPercent(living.getHealth() / living.getMaxHealth());
                for (ServerPlayerEntity player : new ArrayList<>(t.bar().getPlayers())) {
                    if (player.isRemoved() || player.getWorld() != t.world() || player.squaredDistanceTo(living) > BAR_RANGE_SQ) {
                        t.bar().removePlayer(player);
                        if (t.minotaur()) stopMusic(player);
                    }
                }
                for (ServerPlayerEntity player : t.world().getPlayers()) {
                    if (player.squaredDistanceTo(living) > BAR_RANGE_SQ) continue;
                    t.bar().addPlayer(player);
                    if (t.minotaur()) {
                        Integer started = MUSIC_STARTED.get(player.getUuid());
                        if (started == null || server.getTicks() - started > MUSIC_LENGTH_TICKS) {
                            playMusic(player, server.getTicks());
                        }
                    }
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
            MUSIC_STARTED.clear();
            CHAMPIONS.values().forEach(t -> t.bar().clearPlayers());
            CHAMPIONS.clear();
        });
    }

    public static void track(ServerWorld world, LivingEntity champion) {
        if (CHAMPIONS.containsKey(champion.getUuid())) return;
        boolean minotaur = champion instanceof fr.mazecraft.entity.MinotaurEntity;
        ServerBossBar bar = new ServerBossBar(
                minotaur ? champion.getName().copy().formatted(Formatting.DARK_PURPLE)
                        : Text.translatable("mazecraft.champion.name").formatted(Formatting.GOLD),
                minotaur ? BossBar.Color.PURPLE : BossBar.Color.RED,
                minotaur ? BossBar.Style.NOTCHED_20 : BossBar.Style.NOTCHED_10);
        CHAMPIONS.put(champion.getUuid(), new Tracked(world, bar, minotaur));
    }

    /** Boss music, bound to the player (constant volume while they move), "Music" volume slider. */
    private static void playMusic(ServerPlayerEntity player, int now) {
        MUSIC_STARTED.put(player.getUuid(), now);
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket(
                fr.mazecraft.entity.ModSounds.MUSIC_MINOTAUR, net.minecraft.sound.SoundCategory.MUSIC, player, 1.0f, 1.0f,
                player.getRandom().nextLong()));
    }

    private static void stopMusic(ServerPlayerEntity player) {
        if (MUSIC_STARTED.remove(player.getUuid()) != null) {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(
                    fr.mazecraft.entity.ModSounds.MUSIC_MINOTAUR_ID, net.minecraft.sound.SoundCategory.MUSIC));
        }
    }
}
