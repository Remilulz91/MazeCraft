package fr.mazecraft.protection;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.MazeSize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Anti-cheat rules of a maze, active until its central chest has been opened:
 * <ul>
 *   <li>blocks can't be broken inside the maze (walls, floor, chest...);</li>
 *   <li>blocks can't be placed inside the maze (see {@code BlockItemMixin});</li>
 *   <li>players standing on / flying over the walls are sent back to the entrance.</li>
 * </ul>
 * Opening the central chest conquers the maze: protections are lifted for everyone and
 * the advancements are granted. Creative and spectator players are never restricted.
 */
public final class MazeProtection {

    /** How far above the maze bounding box a player still counts as "flying over it". */
    private static final int ABOVE_MARGIN = 64;
    /** Wall-walking check interval (ticks). */
    private static final int CHECK_INTERVAL = 10;

    private MazeProtection() { }

    public static void register() {
        // --- Block breaking
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld) || isExempt(player)) return true;
            if (!MazeCraftConfig.get().protectUntilSolved) return true;
            if (lockedMazeAt(serverWorld, pos, 0) == null) return true;
            player.sendMessage(Text.translatable("mazecraft.protection.cant_break").formatted(Formatting.RED), true);
            return false;
        });

        // --- Opening the central chest conquers the maze
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.CHEST)) return ActionResult.PASS;

            MazeFinder.MazeHit hit = MazeFinder.find(serverWorld, pos, 0);
            if (hit == null || !hit.piece().chestPos().equals(pos)) return ActionResult.PASS;

            if (MazeState.get(serverWorld).markSolved(hit.key())) {
                onConquered(serverWorld, serverPlayer, hit.piece());
            }
            return ActionResult.PASS; // let the chest open normally
        });

        // --- No walking on / flying over the walls
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % CHECK_INTERVAL != 0) return;
            if (!MazeCraftConfig.get().preventWallWalking) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (isExempt(player)) continue;
                checkAboveWalls(world, player);
            }
        });

        MazeCraft.LOGGER.info("[MazeCraft] Maze protection registered");
    }

    /** Called by {@code BlockItemMixin} before a block is placed. */
    public static boolean canPlace(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (player == null || isExempt(player)) return true;
        if (!MazeCraftConfig.get().protectUntilSolved) return true;
        if (lockedMazeAt(world, pos, 0) == null) return true;
        player.sendMessage(Text.translatable("mazecraft.protection.cant_place").formatted(Formatting.RED), true);
        // The client already removed the block from the stack (prediction). The server never
        // did, so re-send the inventory, otherwise the item looks "swallowed" until the next sync.
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.currentScreenHandler.syncState();
        }
        return false;
    }

    private static void checkAboveWalls(ServerWorld world, ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        MazeFinder.MazeHit hit = lockedMazeAt(world, pos, ABOVE_MARGIN);
        if (hit == null) return;
        MazePiece maze = hit.piece();
        if (!maze.isInsideMaze(pos.getX(), pos.getZ())) return;          // margin ring is fine
        if (pos.getY() <= maze.getFloorY() + MazePiece.WALL_HEIGHT) return; // inside the corridors

        BlockPos back = maze.entranceOutsidePos();
        player.teleport(world, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
        player.sendMessage(Text.translatable("mazecraft.protection.no_climbing").formatted(Formatting.RED), true);
    }

    /** The maze at pos if it exists and is not conquered yet, else null. */
    private static MazeFinder.MazeHit lockedMazeAt(ServerWorld world, BlockPos pos, int extraAbove) {
        MazeFinder.MazeHit hit = MazeFinder.find(world, pos, extraAbove);
        if (hit == null || MazeState.get(world).isSolved(hit.key())) return null;
        return hit;
    }

    private static boolean isExempt(PlayerEntity player) {
        return player.isCreative() || player.isSpectator();
    }

    private static void onConquered(ServerWorld world, ServerPlayerEntity player, MazePiece maze) {
        player.sendMessage(Text.translatable("mazecraft.maze.conquered").formatted(Formatting.GOLD), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        grant(player, "conquer_maze");
        if (maze.getSize() == MazeSize.COLOSSAL) {
            grant(player, "conquer_colossal");
        }
    }

    private static void grant(ServerPlayerEntity player, String advancement) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementEntry entry = server.getAdvancementLoader().get(MazeCraft.id(advancement));
        if (entry != null) {
            player.getAdvancementTracker().grantCriterion(entry, "conquered");
        }
    }
}
