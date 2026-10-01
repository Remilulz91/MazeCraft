package fr.mazecraft.protection;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.enemy.MazeAmbush;
import fr.mazecraft.item.KeyFragmentItem;
import fr.mazecraft.item.ModItems;
import fr.mazecraft.progression.MazeProgress;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStyle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ChorusFruitItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Anti-cheat rules of a maze, active until its central chest has been opened:
 * <ul>
 *   <li>blocks can't be broken inside the maze (walls, floor, chest, gates...);</li>
 *   <li>blocks can't be placed inside the maze (see {@code BlockItemMixin});</li>
 *   <li>players standing on / flying over the walls are sent back to the entrance;</li>
 *   <li>no buckets, flint and steel, fire charges or chorus fruit in or near the maze;</li>
 *   <li>ender pearls landing in the maze don't teleport (see {@code EnderPearlEntityMixin});</li>
 *   <li>explosions don't destroy maze blocks (see {@code ExplosionMixin}).</li>
 * </ul>
 * Levers: pulling lever i opens gate i for good (saved per maze, shared by all players).
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

        // --- Levers open gates, the central chest conquers the maze, and a few items are forbidden
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();

            // Fire starters on the hedges
            Item held = player.getStackInHand(hand).getItem();
            if ((held instanceof FlintAndSteelItem || held instanceof FireChargeItem) && !isExempt(player)
                    && MazeCraftConfig.get().protectUntilSolved
                    && lockedMazeAt(serverWorld, pos.offset(hitResult.getSide()), 0) != null) {
                deny(serverPlayer, "mazecraft.protection.forbidden_item");
                return ActionResult.FAIL;
            }

            if (world.getBlockState(pos).isOf(Blocks.LEVER)) {
                MazeFinder.MazeHit hit = MazeFinder.find(serverWorld, pos, 0);
                if (hit != null) {
                    int lever = hit.piece().leverIndexAt(pos);
                    if (lever >= 0 && MazeState.get(serverWorld).openGate(hit.key(), lever)) {
                        openGate(serverWorld, serverPlayer, hit.piece(), lever, hit.key());
                    }
                }
                return ActionResult.PASS; // the lever still flips normally
            }

            if (!world.getBlockState(pos).isOf(Blocks.CHEST)) return ActionResult.PASS;
            MazeFinder.MazeHit hit = MazeFinder.find(serverWorld, pos, 0);
            if (hit == null || !hit.piece().chestPos().equals(pos)) return ActionResult.PASS;

            MazeState state = MazeState.get(serverWorld);
            if (!state.isSolved(hit.key())) {
                // Every lever must have been pulled. Until 0.9.1 the only lock was "a champion
                // is alive", and the champion only appears on the LAST lever — so anyone who
                // reached the plaza without pulling levers (flying over in creative, or any
                // future hole in the walls) opened the chest for free. The rule is now the one
                // the maze is actually built around: open every gate, then face what guards it.
                int closed = firstClosedGate(state, hit.key(), hit.piece().gateCount());
                if (closed >= 0) {
                    deny(serverPlayer, "mazecraft.chest.gates_closed");
                    return ActionResult.FAIL;
                }
                if (state.hasChampion(hit.key())) {
                    deny(serverPlayer, "mazecraft.champion.guarding");
                    return ActionResult.FAIL;
                }
            }

            if (state.markSolved(hit.key())) {
                onConquered(serverWorld, serverPlayer, hit.piece());
            }
            return ActionResult.PASS; // let the chest open normally
        });

        // --- Buckets and chorus fruit in / near an unconquered maze
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)
                    || isExempt(player) || !MazeCraftConfig.get().protectUntilSolved) {
                return TypedActionResult.pass(stack);
            }
            Item item = stack.getItem();
            if (!(item instanceof BucketItem) && !(item instanceof ChorusFruitItem)) {
                return TypedActionResult.pass(stack);
            }
            if (!isNearLockedMaze(serverWorld, player.getBlockPos())) return TypedActionResult.pass(stack);
            deny(serverPlayer, "mazecraft.protection.forbidden_item");
            return TypedActionResult.fail(stack);
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

    /** Used by the mixins: is this position inside an unconquered maze (players' protection rules on)? */
    public static boolean isInLockedMaze(ServerWorld world, BlockPos pos) {
        return MazeCraftConfig.get().protectUntilSolved && lockedMazeAt(world, pos, 0) != null;
    }

    /**
     * Removes from an explosion's block list every block that belongs to an unconquered maze.
     * Maze lookups are cached per X/Z column.
     */
    public static void filterExplosion(ServerWorld world, List<BlockPos> affected) {
        if (!MazeCraftConfig.get().protectUntilSolved || affected.isEmpty()) return;
        Map<Long, Optional<MazeFinder.MazeHit>> cache = new HashMap<>();
        affected.removeIf(pos -> {
            Long chunkKey = BlockPos.asLong(pos.getX(), 0, pos.getZ()); // cache per column
            Optional<MazeFinder.MazeHit> hit = cache.get(chunkKey);
            if (hit == null) {
                MazeFinder.MazeHit found = MazeFinder.findInColumn(world, pos);
                boolean locked = found != null && !MazeState.get(world).isSolved(found.key());
                hit = locked ? Optional.of(found) : Optional.empty();
                cache.put(chunkKey, hit);
            }
            return hit.isPresent() && hit.get().piece().isProtected(pos);
        });
    }

    /** Inside an unconquered maze or within 8 blocks of it (buckets / chorus fruit reach). */
    private static boolean isNearLockedMaze(ServerWorld world, BlockPos pos) {
        for (BlockPos probe : new BlockPos[]{pos, pos.add(8, 0, 0), pos.add(-8, 0, 0), pos.add(0, 0, 8), pos.add(0, 0, -8)}) {
            if (lockedMazeAt(world, probe, ABOVE_MARGIN) != null) return true;
        }
        return false;
    }

    private static void deny(ServerPlayerEntity player, String messageKey) {
        player.sendMessage(Text.translatable(messageKey).formatted(Formatting.RED), true);
        player.currentScreenHandler.syncState();
    }

    private static void openGate(ServerWorld world, ServerPlayerEntity player, MazePiece maze, int gate, long mazeKey) {
        for (BlockPos p : maze.gateBlocks(gate)) {
            world.breakBlock(p, false); // sound + particles, no drop
        }
        boolean last = gate == maze.gateCount() - 1;
        player.sendMessage(Text.translatable(last ? "mazecraft.gate.opened_last" : "mazecraft.gate.opened")
                .formatted(Formatting.GREEN), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.0f, 0.6f);
        grant(player, "pull_lever");
        MazeAmbush.trigger(world, player, maze, gate, mazeKey);
    }

    private static void checkAboveWalls(ServerWorld world, ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        MazeFinder.MazeHit hit = lockedMazeAt(world, pos, ABOVE_MARGIN);
        if (hit == null) return;
        MazePiece maze = hit.piece();
        if (maze.getStyle().enclosed) return;                            // roofed maze: nothing to walk on
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
            if (maze.getStyle().isEnd()) {
                grant(player, "conquer_end_colossal");
            }
        }

        // Progression step: clears this style's current step and unlocks the next one for
        // THIS player only. A colossal maze counts as the large step of its style.
        String step = MazeProgress.advancementId(maze.getStyle(), maze.getSize());
        if (!MazeProgress.hasAdvancement(player, step)) {
            MazeProgress.grant(player, step);
            MazeSize next = nextOf(maze.getSize());
            if (next != null) {
                player.sendMessage(Text.translatable("mazecraft.progression.unlocked",
                        Text.translatable("mazecraft.style." + maze.getStyle().id()),
                        Text.translatable("mazecraft.size." + next.id())).formatted(Formatting.AQUA), false);
            } else if (MazeProgress.isStyleComplete(player, maze.getStyle())) {
                player.sendMessage(Text.translatable("mazecraft.progression.style_complete",
                        Text.translatable("mazecraft.style." + maze.getStyle().id())).formatted(Formatting.GOLD), false);
                giveKeyFragment(world, player, maze);
            }
            if (MazeProgress.isEverythingComplete(player)) {
                player.sendMessage(Text.translatable("mazecraft.maze.all_done").formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
    }

    /** Index of the first gate still closed, or -1 when every lever has been pulled. */
    private static int firstClosedGate(MazeState state, long mazeKey, int gates) {
        for (int i = 0; i < gates; i++) {
            if (!state.isGateOpen(mazeKey, i)) return i;
        }
        return -1;
    }

    /** One fragment per style fully cleared; dropped at the player's feet if the pack is full. */
    private static void giveKeyFragment(ServerWorld world, ServerPlayerEntity player, MazePiece maze) {
        ItemStack fragment = KeyFragmentItem.of(ModItems.KEY_FRAGMENT, maze.getStyle());
        if (!player.getInventory().insertStack(fragment)) {
            player.dropItem(fragment, false);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                SoundCategory.PLAYERS, 1.0f, 1.2f);
        int owned = MazeProgress.completedStyles(player);
        player.sendMessage(Text.translatable("mazecraft.fragment.obtained",
                owned, MazeStyle.values().length).formatted(Formatting.LIGHT_PURPLE), false);
    }

    private static MazeSize nextOf(MazeSize size) {
        return switch (size.step()) {
            case SMALL -> MazeSize.MEDIUM;
            case MEDIUM -> MazeSize.LARGE;
            default -> null;
        };
    }

    private static void grant(ServerPlayerEntity player, String advancement) {
        MazeProgress.grant(player, advancement);
    }
}
