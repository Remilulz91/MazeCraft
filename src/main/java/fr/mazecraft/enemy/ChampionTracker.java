package fr.mazecraft.enemy;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.progression.Fanfare;
import fr.mazecraft.structure.MazeFinder;
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
import net.minecraft.util.math.BlockPos;

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
                keepToThePlaza(t.world(), living);
                shutTheCrypt(t.world(), living);
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
                        openCrypt(world, entity.getBlockPos(), MazeEnemies.homeOf(entity));
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

    /**
     * The guardian of a crypt has fallen, so the chest arrives.
     *
     * <p>Driven by the death itself and not by a tick noticing an absence, for the reason the
     * hoard of Asterion is: "the guardian used to be here and is not any more" is also exactly
     * what a chunk unloading looks like, and a chest must not appear because somebody walked
     * away. {@code placeCryptChest} does nothing if one is already there, so a death that is
     * somehow seen twice costs nothing.</p>
     */
    private static void openCrypt(ServerWorld world, BlockPos where, BlockPos home) {
        // Where it belongs first, where it died only as a fallback. A champion killed a step
        // outside the maze's box finds no maze, and a crypt with no chest in it is a maze that
        // cannot be finished.
        MazeFinder.MazeHit hit = home == null ? null : MazeFinder.find(world, home, 0);
        if (hit == null) hit = MazeFinder.find(world, where, 0);
        if (hit == null || !hit.piece().hasCrypt()) return;
        // The stair first, and outside the guard below: a crypt whose chest somehow already
        // existed must still get its way out back.
        hit.piece().restoreCryptStair(world);
        if (!hit.piece().placeCryptChest(world)) return;
        BlockPos chest = hit.piece().chestPos();
        // A short flight: the crypt's ceiling is eight blocks up, and a rocket with the arena's
        // two seconds detonates in the roof. Cherry pink and the pale wood of the pagoda.
        Fanfare.burst(world, chest, 3.0, 1, 0xF8C3D3, 0xE4B98C);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().isWithinDistance(chest, 48)) {
                player.sendMessage(Text.translatable("mazecraft.crypt.opens")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
        }
    }

    /**
     * Takes the crypt's stair away once a player is in the room with its guardian.
     *
     * <p>The spiral is a block wide and winds round a pole, so nothing with a hitbox can follow
     * anybody up it: three steps up and the guardian cannot be reached, cannot reach, and the
     * fight is target practice. {@code MazePiece.collapseCryptStair} says what is done about it
     * and why sealing the top instead would fix nothing.</p>
     *
     * <p>Here is only the <em>when</em>, and it is not the obvious one. The guardian is spawned
     * when the last lever is pulled, which is minutes of walking from the crypt; collapsing the
     * stair then would take it away before anybody had come down it. So the trigger is a player
     * standing in the room — not on the stair, which is why {@code isInCryptRoom} tests the
     * room's open volume and nothing else.</p>
     *
     * <p>Costs one lookup per tracked champion per five ticks, and only for a champion whose
     * maze has a crypt at all; {@code collapseCryptStair} returns false once there is nothing
     * left to take away, so the sound and the message happen exactly once.</p>
     */
    private static void shutTheCrypt(ServerWorld world, LivingEntity champion) {
        BlockPos tagged = MazeEnemies.homeOf(champion);
        MazeFinder.MazeHit hit = MazeFinder.find(world,
                tagged != null ? tagged : champion.getBlockPos(), 0);
        if (hit == null || !hit.piece().hasCrypt()) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!hit.piece().isInCryptRoom(player.getBlockPos())) continue;
            if (hit.piece().collapseCryptStair(world)) {
                world.playSound(null, player.getBlockPos(),
                        net.minecraft.sound.SoundEvents.BLOCK_STONE_BREAK,
                        net.minecraft.sound.SoundCategory.BLOCKS, 2.0f, 0.5f);
                player.sendMessage(Text.translatable("mazecraft.crypt.shuts")
                        .formatted(Formatting.DARK_RED), false);
            }
            return;
        }
    }

    /** How far a champion may stray from the plaza it guards. */
    private static final double LEASH = 14.0, LEASH_HARD = 28.0;

    /**
     * Keeps a champion in the room it is supposed to be guarding.
     *
     * <p>Champions wander like any mob, and the central chest refuses to open while one is
     * alive — so a zombie that strolls off leaves the player hunting a 101 × 101 maze for it
     * before they can finish. Not fatal, but a miserable twenty minutes. Asterion has its own
     * leash; this is the same idea for every other champion.</p>
     *
     * <p>Nothing is stored for it: the plaza is found from where the champion is standing, so a
     * champion that is already outside its maze is simply left alone rather than being dragged
     * somewhere wrong.</p>
     */
    private static void keepToThePlaza(ServerWorld world, LivingEntity champion) {
        if (champion instanceof fr.mazecraft.entity.MinotaurEntity m && m.isKronos()) return;
        if (!(champion instanceof net.minecraft.entity.mob.MobEntity mob)) return;
        // The champion's own home tag, not where it happens to be standing: a champion that has
        // got outside the maze's box finds no maze, and the leash would give up precisely when
        // it is needed.
        BlockPos tagged = MazeEnemies.homeOf(champion);
        var hit = fr.mazecraft.structure.MazeFinder.find(world,
                tagged != null ? tagged : champion.getBlockPos(), 0);
        if (hit == null) return;
        net.minecraft.util.math.BlockPos home = hit.piece().chestPos();
        double dx = champion.getX() - (home.getX() + 0.5), dz = champion.getZ() - (home.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= LEASH) return;
        if (distance > LEASH_HARD) {
            champion.requestTeleport(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            mob.getNavigation().stop();
            return;
        }
        mob.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
    }

    public static void track(ServerWorld world, LivingEntity champion) {
        if (CHAMPIONS.containsKey(champion.getUuid())) return;
        boolean minotaur = champion instanceof fr.mazecraft.entity.MinotaurEntity;
        // Asterion gets a bar of its own. The Bronze Guardian of a large maze is a serious
        // fight, but it is not the one the whole mod leads to, and two identical purple bars
        // would say they were the same thing.
        boolean asterion = champion instanceof fr.mazecraft.entity.MinotaurEntity m && m.isKronos();
        ServerBossBar bar = new ServerBossBar(
                minotaur ? champion.getName().copy().formatted(asterion ? Formatting.DARK_PURPLE : Formatting.GOLD)
                        : Text.translatable("mazecraft.champion.name").formatted(Formatting.GOLD),
                asterion ? BossBar.Color.PURPLE : minotaur ? BossBar.Color.YELLOW : BossBar.Color.RED,
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
