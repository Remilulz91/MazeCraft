package fr.mazecraft.progression;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.entity.MinotaurEntity;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The door of the arena of Kronos: shut while the fight is on.
 *
 * <p>Step into the arena with the Minotaur alive and the plaza door closes behind you. It opens
 * again when the Minotaur is dead — or when you are, which is the same thing as far as the door
 * is concerned, because what it actually tracks is whether anybody is still in there.</p>
 *
 * <p><b>Nothing is saved, on purpose.</b> The door is not a state that is set and later cleared;
 * every pass works out what it should be from two live facts — is the vault's champion alive,
 * and is anyone inside the room — and makes the blocks match. A crash, a logout mid-swing or a
 * chunk unloading at the wrong moment therefore cannot leave a vault sealed for good: the worst
 * that happens is the door is briefly wrong and the next pass fixes it. A seal that is written
 * down has to be cleared on every path the fight can end by, and one missed path walls the
 * player in permanently.</p>
 */
public final class KronosArena {

    /** Ticks between two passes. */
    private static final int INTERVAL = 10;

    private KronosArena() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % INTERVAL != 0) return;
            if (!world.getRegistryKey().equals(World.OVERWORLD)) return;
            if (!MazeCraftConfig.get().enableChampion) return;

            Set<Long> seen = new HashSet<>();
            for (ServerPlayerEntity player : world.getPlayers()) {
                MazeFinder.MazeHit hit = MazeFinder.find(world, player.getBlockPos(), 0);
                if (hit == null || !hit.piece().getStyle().isKronos()) continue;
                if (!seen.add(hit.key())) continue;
                enforce(world, hit.piece(), hit.key());
            }
        });
        MazeCraft.LOGGER.info("[MazeCraft] Kronos arena registered");
    }

    /**
     * Asterion has fallen. Opens the hoard, once and for all.
     *
     * <p>Driven by the death itself rather than by the tick noticing an absence: "the boss used
     * to be here and is not any more" is also what a chunk unloading looks like, and the hoard
     * must not open because somebody walked away.</p>
     */
    public static void onAsterionSlain(ServerWorld world, BlockPos where) {
        MazeFinder.MazeHit hit = MazeFinder.find(world, where, 0);
        if (hit == null || !hit.piece().getStyle().isKronos()) return;
        MazeState state = MazeState.get(world);
        if (!state.markSolved(hit.key())) return;   // already open: nothing to do twice

        KronosWalls.clearFrenzy(hit.key());
        hit.piece().openHoard(world);
        celebrate(world, hit.piece().chestPos());

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().isWithinDistance(where, 48)) {
                player.sendMessage(Text.translatable("mazecraft.kronos.hoard")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
        }
    }

    /** Fireworks over the arena, gold and verdigris. */
    private static void celebrate(ServerWorld world, BlockPos centre) {
        for (int i = 0; i < 12; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(2, List.of(
                    new FireworkExplosionComponent(
                            i % 2 == 0 ? FireworkExplosionComponent.Type.LARGE_BALL
                                       : FireworkExplosionComponent.Type.STAR,
                            IntList.of(0xFFD700, 0x4F9B7F),
                            IntList.of(0xFFFFFF),
                            true, true))));
            double angle = Math.PI * 2 * i / 12.0;
            world.spawnEntity(new FireworkRocketEntity(world,
                    centre.getX() + 0.5 + Math.cos(angle) * 5.0,
                    centre.getY() - 1.0,
                    centre.getZ() + 0.5 + Math.sin(angle) * 5.0,
                    rocket));
        }
        world.playSound(null, centre, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, centre, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER,
                SoundCategory.BLOCKS, 2.0f, 0.5f);
    }

    private static void enforce(ServerWorld world, MazePiece maze, long mazeKey) {
        MazeState state = MazeState.get(world);
        // Both conditions are read from live entities rather than from a saved flag, and the
        // Minotaur must be in the room with you. The saved "this vault has a champion" flag
        // would have been enough to shut the door — and that is exactly the trap: the Minotaur
        // wanders, so it can leave the arena through the open door before you walk in, and the
        // flag would then seal you into an empty room with the boss on the other side.
        boolean fighting = bossInArena(world, maze) && someoneInside(world, maze);
        if (!fighting) {
            // The fight is over, or never started. Nothing to undo beyond the door itself.
            KronosWalls.clearFrenzy(mazeKey);
        }

        // The arena's own door is the last gate: the one the final lever opens.
        int door = maze.gateCount() - 1;
        if (door < 0) return;

        // Only ever touch a door the player has already earned. Without this the pass would
        // happily "open" a door that is shut because its lever has not been pulled — this runs
        // for any player anywhere in the vault, so standing at the entrance of a vault with no
        // fight going on would have unlocked the arena for free.
        if (!state.isGateOpen(mazeKey, door)) return;

        List<BlockPos> blocks = maze.gateBlocks(door);
        if (blocks.isEmpty()) return;

        BlockState wanted = fighting ? maze.getStyle().gate : Blocks.AIR.getDefaultState();
        boolean shut = !world.getBlockState(blocks.get(0)).isAir();
        if (shut == fighting) return;

        for (BlockPos pos : blocks) {
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            world.setBlockState(pos, wanted, Block.NOTIFY_LISTENERS);
        }
        world.playSound(null, blocks.get(0),
                fighting ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_IRON_DOOR_OPEN,
                SoundCategory.BLOCKS, 1.4f, 0.4f);
    }

    /** Is a living Minotaur of Kronos standing in the arena right now? */
    private static boolean bossInArena(ServerWorld world, MazePiece maze) {
        BlockPos centre = maze.chestPos();
        Box around = new Box(centre).expand(14.0, 8.0, 14.0);
        for (MinotaurEntity boss : world.getEntitiesByClass(MinotaurEntity.class, around,
                m -> m.isAlive() && m.isKronos())) {
            if (maze.isInPlaza(boss.getBlockPos())) return true;
        }
        return false;
    }

    private static boolean someoneInside(ServerWorld world, MazePiece maze) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative()) continue;
            if (maze.isInPlaza(player.getBlockPos())) return true;
        }
        return false;
    }
}
