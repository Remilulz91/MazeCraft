package fr.mazecraft.progression;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.block.ModBlocks;
import fr.mazecraft.block.SealedGatewayBlock;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import fr.mazecraft.structure.MazeSize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import fr.mazecraft.item.KronosKeyItem;
import fr.mazecraft.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the per-player progression at a maze entrance.
 *
 * <p>Three layers, because no single one is enough:</p>
 * <ol>
 *   <li><b>The gateway block</b> ({@link SealedGatewayBlock}) is the visible signal. It has no
 *       collision, so a player who <em>has</em> earned the way walks straight through with no
 *       per-player block trickery.</li>
 *   <li><b>{@link #onTouch}</b> shoves back a player who has not, with a message naming the step
 *       they still owe. This is what the player actually experiences.</li>
 *   <li><b>{@link #sweep}</b> teleports out anyone who ended up inside anyway. A one-block-thick
 *       plane without collision can be crossed in a single tick at elytra speed, so this sweep —
 *       not the block — is the real guarantee.</li>
 * </ol>
 *
 * <p>The sweep also retro-fits the gateway onto mazes generated before 0.9.0, so existing worlds
 * do not have to be thrown away.</p>
 */
public final class MazeBarrier {

    /** Ticks between two access checks per player. */
    private static final int ACCESS_INTERVAL = 10;

    /** Ticks between two retro-fit passes; much rarer, it reads chunk structure references. */
    private static final int RETROFIT_INTERVAL = 40;

    /** Ticks a player is left alone after being told they may not pass, so it does not spam. */
    private static final int MESSAGE_COOLDOWN = 40;

    /** How hard a blocked player is shoved back. */
    private static final double PUSH_BACK = 0.55;

    private static final Map<UUID, Long> lastMessage = new HashMap<>();

    private MazeBarrier() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long time = world.getTime();
            boolean access = time % ACCESS_INTERVAL == 0;
            boolean retrofit = time % RETROFIT_INTERVAL == 0;
            if (!access && !retrofit) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (retrofit) retrofitNearby(world, player);
                if (access) sweep(world, player);
            }
        });
        MazeCraft.LOGGER.info("[MazeCraft] Maze barrier registered");
    }

    // === Layer 2: contact ===

    /** Called by the gateway block when something walks into it. */
    public static void onTouch(World world, BlockPos pos, Entity entity) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        // A player in a boat or on a horse collides as the vehicle, not as themselves.
        Entity rider = entity instanceof PlayerEntity ? entity : entity.getControllingPassenger();
        if (!(rider instanceof ServerPlayerEntity player) || isExempt(player)) return;

        MazeFinder.MazeHit hit = MazeFinder.find(serverWorld, pos, 0);
        if (hit == null) return;
        MazePiece maze = hit.piece();
        if (mayEnter(player, maze)) return;

        // Shove the player back out along the line from the maze centre.
        Vec3d centre = Vec3d.ofCenter(maze.chestPos());
        Vec3d away = new Vec3d(player.getX() - centre.x, 0, player.getZ() - centre.z);
        away = away.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : away.normalize();
        Entity pushed = entity == player ? player : entity; // push the vehicle if there is one
        pushed.setVelocity(away.x * PUSH_BACK, 0.12, away.z * PUSH_BACK);
        pushed.velocityModified = true;

        warn(serverWorld, player, maze);
    }

    /**
     * May this player walk into this maze? An ordinary maze asks for the previous step of its own
     * style; the Labyrinth of Kronos asks for all of them.
     */
    private static boolean mayEnter(ServerPlayerEntity player, MazePiece maze) {
        // Kronos asks for the door to have been opened with the key, not merely for the 48
        // steps. The steps are what earns the key; turning it is what opens the door.
        if (maze.getStyle().isKronos()) {
            return MazeProgress.hasAdvancement(player, KronosKeyItem.UNSEALED);
        }
        return MazeProgress.canEnter(player, maze.getStyle(), maze.getSize());
    }

    /** Hands back a Key of Kronos to a player who has earned one and no longer has it. */
    private static void giveKeyIfMissing(ServerWorld world, ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.KRONOS_KEY)) return;
        }
        ItemStack key = KronosKeyItem.forPlayer(ModItems.KRONOS_KEY, player);
        if (!player.getInventory().insertStack(key)) player.dropItem(key, false);
        player.sendMessage(Text.translatable("mazecraft.kronos.key_returned")
                .formatted(Formatting.LIGHT_PURPLE), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void warn(ServerWorld world, ServerPlayerEntity player, MazePiece maze) {
        long now = world.getTime();
        Long last = lastMessage.get(player.getUuid());
        if (last != null && now - last < MESSAGE_COOLDOWN) return;
        lastMessage.put(player.getUuid(), now);

        if (maze.getStyle().isKronos()) {
            if (MazeProgress.isEverythingComplete(player)) {
                // Earned it, but has not turned the key. If the key itself is gone — lost in
                // lava, left in a chest a world away — the lock gives them another rather than
                // shutting them out of the whole endgame over a dropped item.
                player.sendMessage(Text.translatable("mazecraft.kronos.use_key")
                        .formatted(Formatting.LIGHT_PURPLE), true);
                giveKeyIfMissing(world, player);
            } else {
                player.sendMessage(Text.translatable("mazecraft.barrier.kronos",
                        MazeProgress.completedSteps(player), MazeProgress.TOTAL_STEPS)
                        .formatted(Formatting.RED), true);
            }
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    SoundCategory.BLOCKS, 0.5f, 0.6f);
            return;
        }

        MazeSize required = maze.getSize().previousStep();
        if (required != null) {
            // Naming the missing step is deliberate: a plain "access denied" leaves the player
            // with no way to work out the rule from inside the game.
            player.sendMessage(Text.translatable("mazecraft.barrier.locked",
                    Text.translatable("mazecraft.size." + required.id()),
                    Text.translatable("mazecraft.style." + maze.getStyle().id()))
                    .formatted(Formatting.RED), true);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.BLOCKS, 0.4f, 1.8f);
    }

    // === Layer 3: the sweep that actually guarantees the gate ===

    private static void sweep(ServerWorld world, ServerPlayerEntity player) {
        if (isExempt(player)) return;
        BlockPos pos = player.getBlockPos();
        MazeFinder.MazeHit hit = MazeFinder.find(world, pos, 0);
        if (hit == null) return;
        MazePiece maze = hit.piece();
        if (!maze.isInsideMaze(pos.getX(), pos.getZ())) return;
        if (mayEnter(player, maze)) return;

        BlockPos out = maze.entranceOutsidePos();
        player.teleport(world, out.getX() + 0.5, out.getY(), out.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        player.fallDistance = 0;
        warn(world, player, maze);
    }

    // === Retro-fit: mazes generated before 0.9.0 have no gateway block ===

    private static void retrofitNearby(ServerWorld world, ServerPlayerEntity player) {
        for (MazeFinder.MazeHit hit : MazeFinder.findInChunk(world, player.getChunkPos())) {
            ensureGateway(world, hit.piece());
        }
    }

    /**
     * Makes sure the gateway is whole: places it on a maze generated before 0.9.0, and closes any
     * gap punched in an existing one.
     *
     * <p>The plane is only a dozen blocks, so every one of them is checked rather than probing a
     * single corner — a hole knocked out of the middle would otherwise stay open for good. In
     * practice this makes the gateway self-healing: a player in creative can break it, as they
     * can break bedrock, and it closes again on the next sweep.</p>
     */
    public static void ensureGateway(ServerWorld world, MazePiece maze) {
        SealedGatewayBlock.Tier tier = SealedGatewayBlock.Tier.of(maze.getStyle(), maze.getSize());
        if (tier == null) return; // small mazes are never sealed

        BlockState gateway = ModBlocks.SEALED_GATEWAY.getDefaultState()
                .with(SealedGatewayBlock.AXIS, maze.entranceAlongX() ? Direction.Axis.X : Direction.Axis.Z)
                .with(SealedGatewayBlock.TIER, tier);
        int placed = 0;
        for (BlockPos pos : maze.entranceBlocks()) {
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            // Only fill what is open: never overwrite the walls, or anything a player put there.
            if (!world.getBlockState(pos).isAir()) continue;
            world.setBlockState(pos, gateway);
            placed++;
        }
        if (placed > 0) {
            MazeCraft.LOGGER.debug("[MazeCraft] Sealed gateway closed on {} ({} blocks)",
                    maze.chestPos(), placed);
        }
    }

    private static boolean isExempt(PlayerEntity player) {
        return player.isCreative() || player.isSpectator();
    }
}
