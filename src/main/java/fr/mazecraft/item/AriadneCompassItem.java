package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.progression.MazeProgress;
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

    /** Search radius, in chunks — the same as vanilla {@code /locate}. */
    private static final int SEARCH_RADIUS = 100;

    /** Cooldown between two searches: locating a structure is expensive for the server. */
    private static final int COOLDOWN = 100;

    private static final String TARGET_KEY = "MazeTarget";

    public AriadneCompassItem(Settings settings) {
        super(settings);
    }

    /** A step the holder still has to clear: one style, one size. */
    public record Target(MazeStyle style, MazeSize size) {
        public String key() {
            return style.id() + "/" + size.step().id();
        }

        public Text label() {
            return Text.translatable("mazecraft.maze.name",
                    Text.translatable("mazecraft.style." + style.id()),
                    Text.translatable("mazecraft.size." + size.step().id()));
        }
    }

    /** Every step the player still owes in this dimension, one per style, in enum order. */
    public static List<Target> remainingTargets(ServerPlayerEntity player, World world) {
        List<Target> targets = new ArrayList<>();
        for (MazeStyle style : MazeProgress.stylesOf(world)) {
            MazeSize next = MazeProgress.nextStep(player, style);
            if (next != null) targets.add(new Target(style, next));
        }
        return targets;
    }

    private static String storedKey(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? "" : data.copyNbt().getString(TARGET_KEY);
    }

    private static void store(ItemStack stack, Target target) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(TARGET_KEY, target.key());
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT,
                current -> NbtComponent.of(merge(current.copyNbt(), nbt)));
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

        BlockPos found = locate(serverWorld, current, user.getBlockPos());
        if (found == null) {
            player.sendMessage(Text.translatable("mazecraft.compass.not_found",
                    current.label().copy().formatted(Formatting.AQUA)).formatted(Formatting.GRAY), false);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS, 0.6f, 0.7f);
            return TypedActionResult.success(stack, false);
        }

        int distance = (int) Math.sqrt(user.getBlockPos().getSquaredDistance(
                new BlockPos(found.getX(), user.getBlockY(), found.getZ())));
        player.sendMessage(Text.translatable("mazecraft.compass.found",
                current.label().copy().formatted(Formatting.AQUA),
                Text.literal(String.valueOf(distance)).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getX())).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getZ())).formatted(Formatting.WHITE))
                .formatted(Formatting.GOLD), false);
        world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.7f, 1.2f);
        drawBearing(serverWorld, user, found);
        return TypedActionResult.success(stack, false);
    }

    /**
     * Locates the structure of this exact style and size. Every step has its own structure tag
     * ({@code #mazecraft:step/<style>_<size>}) holding a single structure, which is what lets a
     * tag-based lookup target one precise step.
     */
    private static BlockPos locate(ServerWorld world, Target target, BlockPos from) {
        TagKey<Structure> tag = TagKey.of(RegistryKeys.STRUCTURE,
                MazeCraft.id("step/" + target.style().id() + "_" + target.size().step().id()));
        return world.locateStructure(tag, from, SEARCH_RADIUS, false);
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
