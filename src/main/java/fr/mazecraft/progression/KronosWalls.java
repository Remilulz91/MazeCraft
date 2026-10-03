package fr.mazecraft.progression;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shifting walls of the Labyrinth of Kronos.
 *
 * <p>Every {@link #PERIOD} the vault rearranges: a third of its movable segments stand open and
 * the rest are shut, and which third it is rotates. Shortcuts appear and are taken away while
 * the player is still inside.</p>
 *
 * <p><b>Why this can never trap anyone.</b> The maze is a tree — exactly one path between any
 * two cells — so opening a wall only ever adds a loop, and closing it again restores the tree.
 * Connectivity is therefore guaranteed by construction, with no check at any point: the centre
 * and the way out stay reachable whatever the walls are doing. The segments are also chosen so
 * that both sides lie in the same zone, so a loop can never become a way past a gate. Both
 * properties are verified over hundreds of generated mazes rather than argued.</p>
 *
 * <p>Which segments are open is a pure function of the world time, so nothing has to be saved
 * and a reload cannot disagree with what the blocks say. The tick only makes the world match
 * the schedule — if a segment could not be closed because somebody was standing in it, the next
 * pass closes it.</p>
 */
public final class KronosWalls {

    /** How long one arrangement lasts. */
    private static final int PERIOD = 900; // 45 seconds

    /** How long one arrangement lasts while the Minotaur of Kronos is in its second phase. */
    private static final int FRENZY_PERIOD = 200; // 10 seconds

    /**
     * Vaults whose walls are frantic, by maze key.
     *
     * <p>Deliberately not saved. The frenzy belongs to a fight that is happening right now, and
     * a reload ends it — the walls fall back to their 45-second rhythm, which is a state the
     * blocks can always be brought into. Saving it would mean a vault that could be left frantic
     * for good by a crash at the wrong moment.</p>
     */
    private static final Set<Long> FRENZIED = new HashSet<>();

    /** Turns the frenzy on or off for whichever Kronos vault contains {@code inside}. */
    public static void setFrenzy(ServerWorld world, BlockPos inside, boolean on) {
        MazeFinder.MazeHit hit = MazeFinder.find(world, inside, 0);
        if (hit == null || !hit.piece().getStyle().isKronos()) return;
        if (on) FRENZIED.add(hit.key()); else FRENZIED.remove(hit.key());
    }

    /** Called when a vault's fight ends, however it ends. */
    public static void clearFrenzy(long mazeKey) {
        FRENZIED.remove(mazeKey);
    }

    /** How long before a shift the warning is given. */
    private static final int WARNING = 40; // 2 seconds

    /** One segment in this many stands open at a time. */
    private static final int OPEN_IN = 3;

    /** Ticks between two passes. */
    private static final int INTERVAL = 10;

    /** Segments further than this from every player are left alone. */
    private static final int RANGE = 48;

    /** A segment is never closed on top of anyone this close to it. */
    private static final double CLEARANCE = 2.0;

    private KronosWalls() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % INTERVAL != 0) return;
            if (!world.getRegistryKey().equals(World.OVERWORLD)) return;
            if (!MazeCraftConfig.get().enableMovingWalls) return;

            Set<Long> done = new HashSet<>();
            for (ServerPlayerEntity player : world.getPlayers()) {
                MazeFinder.MazeHit hit = MazeFinder.find(world, player.getBlockPos(), 0);
                if (hit == null || !hit.piece().getStyle().isKronos()) continue;
                if (!done.add(hit.key())) continue;
                shift(world, hit.piece(), hit.key());
            }
        });
        MazeCraft.LOGGER.info("[MazeCraft] Shifting walls registered");
    }

    /** True when segment {@code index} should stand open during {@code phase}. */
    private static boolean openDuring(long phase, int index) {
        return Math.floorMod(phase + index, OPEN_IN) == 0;
    }

    private static void shift(ServerWorld world, MazePiece maze, long mazeKey) {
        long time = world.getTime();
        int period = FRENZIED.contains(mazeKey) ? FRENZY_PERIOD : PERIOD;
        long phase = Math.floorDiv(time, period);
        boolean warning = time % period >= period - WARNING;

        BlockState wall = maze.movableWallState();
        BlockState air = Blocks.AIR.getDefaultState();
        int count = maze.movableWalls().size();

        for (int i = 0; i < count; i++) {
            // Cheap distance test first: a vault has well over a hundred segments and all but a
            // few are far from anyone, so the far ones must not cost a list of block positions.
            if (!nearAnyPlayer(world, maze.movableWallAnchor(i))) continue;
            List<BlockPos> blocks = maze.movableWallBlocks(i);
            if (blocks.isEmpty()) continue;

            boolean shouldBeOpen = openDuring(phase, i);
            if (warning && !openDuring(phase + 1, i) && shouldBeOpen) {
                warn(world, blocks);
            }

            boolean isOpen = world.getBlockState(blocks.get(0)).isAir();
            if (isOpen == shouldBeOpen) continue;

            // Closing on someone would suffocate them. Leave it open; the next pass tries again,
            // which is why the schedule is a target rather than a sequence of events.
            if (!shouldBeOpen && occupied(world, blocks)) continue;

            for (BlockPos pos : blocks) {
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                world.setBlockState(pos, shouldBeOpen ? air : wall, Block.NOTIFY_LISTENERS);
            }
            BlockPos at = blocks.get(0);
            world.playSound(null, at, shouldBeOpen ? SoundEvents.BLOCK_PISTON_EXTEND
                            : SoundEvents.BLOCK_PISTON_CONTRACT,
                    SoundCategory.BLOCKS, 1.2f, 0.45f);
        }
    }

    private static void warn(ServerWorld world, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            world.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0.0);
        }
        world.playSound(null, blocks.get(0), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.BLOCKS, 0.5f, 1.8f);
    }

    private static boolean nearAnyPlayer(ServerWorld world, BlockPos pos) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().isWithinDistance(pos, RANGE)) return true;
        }
        return false;
    }

    /** True if anybody is standing in or against this segment. */
    private static boolean occupied(ServerWorld world, List<BlockPos> blocks) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            Vec3d at = player.getPos();
            for (BlockPos pos : blocks) {
                if (at.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                        < CLEARANCE * CLEARANCE) return true;
            }
        }
        return false;
    }
}
