package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.progression.MazeProgress;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStyle;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Ariadne's Compass: points to the maze the holder has to clear next.
 *
 * <p>With 16 styles × 3 steps to complete, hunting for one specific style and size by flying
 * around is not a game. The compass turns that search into a decision: it lists the steps the
 * player still owes in the dimension they are standing in, and locates the one they pick.</p>
 *
 * <ul>
 *   <li>right-click — locate the current target (coordinates, distance, particle bearing);</li>
 *   <li>sneak + right-click — switch to the next uncleared target of this dimension.</li>
 * </ul>
 *
 * <p>Targets are per player, because progression is: two players holding the same compass are
 * not offered the same mazes. The target is stored on the stack, so a compass handed over keeps
 * pointing where it pointed — it just becomes useless to anyone who is not at that step.</p>
 */
public class AriadneCompassItem extends Item {

    /**
     * Radius of ONE sweep, in rings of the structure set's spread grid — not in chunks. See
     * {@link StructureSearch} for why that distinction cost two servers.
     *
     * <p>12 rings of a maze set's 40-chunk spacing is 625 candidate sites and 7,680 blocks,
     * which is a sweep a tick can afford. It replaces 100, which was 40,401 sites.</p>
     */
    private static final int SEARCH_RADIUS = 12;

    /** Blocks one sweep reaches from its own origin. */
    private static final int SWEEP_REACH = StructureSearch.reach(SEARCH_RADIUS, 40);

    /** Cooldown between two searches: locating a structure is expensive for the server. */
    private static final int COOLDOWN = 100;

    private static final String TARGET_KEY = "MazeTarget";

    /** Which sweep the next use runs, stored on the stack so the search can be resumed. */
    private static final String SWEEP_KEY = "MazeSweep";

    /** A maze found but already conquered, kept in case the whole search turns up nothing else. */
    private static final String CLEARED_KEY = "MazeCleared";

    public AriadneCompassItem(Settings settings) {
        super(settings);
    }

    /** A step the holder still has to clear: one style, one size. */
    public record Target(MazeStyle style, MazeSize size) {
        public String key() {
            return style.id() + "/" + size.step().id();
        }

        public Text label() {
            if (style.isKronos()) return Text.translatable("mazecraft.kronos.target");
            return Text.translatable("mazecraft.maze.name",
                    Text.translatable("mazecraft.style." + style.id()),
                    Text.translatable("mazecraft.size." + size.step().id()));
        }

        /**
         * The structure tag to search. Every ordinary step has a tag of its own holding one
         * structure, which is what lets a tag lookup target one precise step; Kronos is one of
         * a kind and has its own.
         */
        public String tag() {
            return style.isKronos() ? "kronos" : "step/" + style.id() + "_" + size.step().id();
        }
    }

    /** Every step the player still owes in this dimension, one per style, in enum order. */
    public static List<Target> remainingTargets(ServerPlayerEntity player, World world) {
        List<Target> targets = new ArrayList<>();
        for (MazeStyle style : MazeProgress.stylesOf(world)) {
            MazeSize next = MazeProgress.nextStep(player, style);
            if (next != null) targets.add(new Target(style, next));
        }
        // Once every step is cleared the list would be empty and the compass a dead item for
        // the whole endgame. What is left owed at that point is Kronos, so it becomes the last
        // target — in the Overworld, where it is buried.
        if (targets.isEmpty() && world.getRegistryKey().equals(World.OVERWORLD)
                && MazeProgress.isEverythingComplete(player)) {
            targets.add(new Target(MazeStyle.KRONOS, MazeSize.COLOSSAL));
        }
        return targets;
    }

    private static String storedKey(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? "" : data.copyNbt().getString(TARGET_KEY);
    }

    /** Sets the target, and starts its search over whenever the target actually changed. */
    private static void store(ItemStack stack, Target target) {
        if (target.key().equals(storedKey(stack))) return;
        NbtCompound nbt = new NbtCompound();
        nbt.putString(TARGET_KEY, target.key());
        nbt.putInt(SWEEP_KEY, 0);
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, current -> {
            NbtCompound base = current.copyNbt();
            base.remove(CLEARED_KEY);
            return NbtComponent.of(merge(base, nbt));
        });
    }

    private static BlockPos storedCleared(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        NbtCompound nbt = data.copyNbt();
        return nbt.contains(CLEARED_KEY) ? BlockPos.fromLong(nbt.getLong(CLEARED_KEY)) : null;
    }

    /** Records where the search stands: which sweep comes next, and the best miss so far. */
    private static void storeSweep(ItemStack stack, int sweep, BlockPos cleared) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt(SWEEP_KEY, sweep);
        if (cleared != null) nbt.putLong(CLEARED_KEY, cleared.asLong());
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, current -> {
            NbtCompound base = current.copyNbt();
            if (cleared == null) base.remove(CLEARED_KEY);
            return NbtComponent.of(merge(base, nbt));
        });
    }

    private static NbtCompound merge(NbtCompound base, NbtCompound extra) {
        base.copyFrom(extra);
        return base;
    }

    /** The stack's target if it is still relevant, otherwise the first one the player owes. */
    private static Target resolve(ItemStack stack, List<Target> remaining) {
        String key = storedKey(stack);
        for (Target t : remaining) {
            if (t.key().equals(key)) return t;
        }
        return remaining.isEmpty() ? null : remaining.get(0);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient());
        }

        List<Target> remaining = remainingTargets(player, world);
        if (remaining.isEmpty()) {
            player.sendMessage(Text.translatable("mazecraft.compass.no_target").formatted(Formatting.GREEN), true);
            return TypedActionResult.fail(stack);
        }

        Target current = resolve(stack, remaining);

        // Sneaking cycles to the next target instead of searching — cheap, no cooldown.
        if (user.isSneaking()) {
            int index = remaining.indexOf(current);
            Target next = remaining.get((index + 1) % remaining.size());
            store(stack, next);
            player.sendMessage(Text.translatable("mazecraft.compass.switched",
                    next.label().copy().formatted(Formatting.AQUA)), true);
            world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundCategory.PLAYERS, 0.4f, 1.6f);
            return TypedActionResult.success(stack, false);
        }

        store(stack, current);
        user.getItemCooldownManager().set(this, COOLDOWN);

        int sweep = StructureSearch.storedSweep(stack, SWEEP_KEY);
        BlockPos here = user.getBlockPos();
        BlockPos found = StructureSearch.locate(serverWorld,
                TagKey.of(RegistryKeys.STRUCTURE, MazeCraft.id(current.tag())),
                StructureSearch.sweepOrigin(here, sweep, SWEEP_REACH),
                SEARCH_RADIUS, current.tag());

        // A maze somebody already emptied is not a place to send anyone — but it is still worth
        // remembering, so a world where every one of them is done points somewhere in the end
        // rather than claiming there is none. The start chunk locateStructure returns is exactly
        // what MazeState is keyed on, so this costs no chunk load and no structure read.
        BlockPos cleared = storedCleared(stack);
        if (found != null && MazeState.get(serverWorld).isSolved(new ChunkPos(found).toLong())) {
            if (cleared == null) cleared = found;
            found = null;
        }

        if (found != null) {
            storeSweep(stack, 0, null);
            announce(serverWorld, player, current, found, "mazecraft.compass.found", Formatting.GOLD);
            world.playSound(null, here, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.7f, 1.2f);
            return TypedActionResult.success(stack, false);
        }

        if (sweep < StructureSearch.LAST_SWEEP) {
            storeSweep(stack, sweep + 1, cleared);
            player.sendMessage(Text.translatable("mazecraft.compass.sweeping",
                    current.label().copy().formatted(Formatting.AQUA),
                    Text.literal(String.valueOf(sweep + 1)).formatted(Formatting.WHITE),
                    Text.literal(String.valueOf(StructureSearch.LAST_SWEEP + 1)).formatted(Formatting.WHITE))
                    .formatted(Formatting.GRAY), false);
            world.playSound(null, here, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS, 0.5f, 1.3f);
            return TypedActionResult.success(stack, false);
        }

        storeSweep(stack, 0, null);
        if (cleared != null) {
            announce(serverWorld, player, current, cleared, "mazecraft.compass.cleared", Formatting.YELLOW);
            world.playSound(null, here, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.5f, 0.9f);
            return TypedActionResult.success(stack, false);
        }
        player.sendMessage(Text.translatable("mazecraft.compass.not_found",
                current.label().copy().formatted(Formatting.AQUA)).formatted(Formatting.GRAY), false);
        world.playSound(null, here, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                SoundCategory.PLAYERS, 0.6f, 0.7f);
        return TypedActionResult.success(stack, false);
    }

    private static void announce(ServerWorld world, ServerPlayerEntity player, Target target,
                                 BlockPos found, String key, Formatting colour) {
        BlockPos here = player.getBlockPos();
        int distance = (int) Math.sqrt(here.getSquaredDistance(
                new BlockPos(found.getX(), here.getY(), found.getZ())));
        player.sendMessage(Text.translatable(key,
                target.label().copy().formatted(Formatting.AQUA),
                Text.literal(String.valueOf(distance)).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getX())).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getZ())).formatted(Formatting.WHITE))
                .formatted(colour), false);
        drawBearing(world, player, found);
    }

    /** A short line of particles from the player toward the maze, so the bearing is readable. */
    private static void drawBearing(ServerWorld world, PlayerEntity user, BlockPos target) {
        Vec3d origin = user.getPos().add(0, 1.2, 0);
        Vec3d direction = new Vec3d(target.getX() - origin.x, 0, target.getZ() - origin.z).normalize();
        for (int i = 1; i <= 14; i++) {
            Vec3d p = origin.add(direction.multiply(i * 0.8));
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String key = storedKey(stack);
        if (!key.isEmpty()) {
            String[] parts = key.split("/");
            MazeStyle style = parts.length == 2 ? MazeStyle.fromId(parts[0]) : null;
            MazeSize size = parts.length == 2 ? MazeSize.fromId(parts[1]) : null;
            if (style != null && size != null) {
                tooltip.add(Text.translatable("mazecraft.compass.target",
                        new Target(style, size).label()).formatted(Formatting.AQUA));
            }
        }
        tooltip.add(Text.translatable("mazecraft.compass.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
