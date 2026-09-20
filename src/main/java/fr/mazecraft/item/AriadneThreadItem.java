package fr.mazecraft.item;

import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Ariadne's Thread.
 * <ul>
 *   <li>Inside a maze: a golden thread traces the way to the current objective (next lever,
 *       then the chest) along the corridors, for a few seconds.</li>
 *   <li>Outside: the thread points toward the nearest maze of this dimension, and the action
 *       bar gives its distance and direction.</li>
 * </ul>
 * 8 uses, 5 s cooldown.
 */
public class AriadneThreadItem extends Item {

    public static final int USES = 8;
    private static final int COOLDOWN_TICKS = 100;
    /** Blocks of path shown inside a maze. */
    private static final int PATH_SHOWN = 48;
    /** Search radius for the nearest maze (chunks). Same order as explorer maps. */
    private static final int LOCATE_RADIUS = 64;

    private static final TagKey<Structure> MAZES = TagKey.of(RegistryKeys.STRUCTURE, Identifier.of("mazecraft", "mazes"));

    public AriadneThreadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld server) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, true);
        }
        boolean used = inMaze(server, player) || towardMaze(server, player);
        if (!used) return TypedActionResult.fail(stack);

        player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        server.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.PLAYERS, 1.0f, 1.2f);
        stack.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return TypedActionResult.success(stack, false);
    }

    /** Inside a maze: trace the corridors to the next objective. */
    private boolean inMaze(ServerWorld world, ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        MazeFinder.MazeHit hit = MazeFinder.find(world, pos, 0);
        if (hit == null) return false;
        MazePiece maze = hit.piece();
        if (!maze.isInsideMaze(pos.getX(), pos.getZ())) return false;

        MazeState state = MazeState.get(world);
        long key = hit.key();
        List<BlockPos> path = maze.threadPath(pos, gate -> state.isSolved(key) || state.isGateOpen(key, gate));
        if (path.size() <= 1) {
            player.sendMessage(Text.translatable("mazecraft.thread.here").formatted(Formatting.GOLD), true);
            return true;
        }
        List<Vec3d> points = new ArrayList<>();
        for (int i = 0; i < Math.min(PATH_SHOWN, path.size()); i++) {
            points.add(Vec3d.ofBottomCenter(path.get(i)).add(0, 0.3, 0));
        }
        ThreadTrails.show(player, points);
        player.sendMessage(Text.translatable("mazecraft.thread.follow", path.size()).formatted(Formatting.GOLD), true);
        return true;
    }

    /** Outside: point toward the nearest maze of this dimension. */
    private boolean towardMaze(ServerWorld world, ServerPlayerEntity player) {
        BlockPos found = world.locateStructure(MAZES, player.getBlockPos(), LOCATE_RADIUS, false);
        if (found == null) {
            player.sendMessage(Text.translatable("mazecraft.thread.none").formatted(Formatting.GRAY), true);
            return false;
        }
        Vec3d eyes = player.getEyePos();
        Vec3d dir = new Vec3d(found.getX() + 0.5 - eyes.x, 0, found.getZ() + 0.5 - eyes.z);
        double distance = dir.length();
        dir = dir.normalize();
        List<Vec3d> points = new ArrayList<>();
        for (int i = 2; i <= 20; i++) points.add(eyes.add(dir.multiply(i)).add(0, -0.4, 0));
        ThreadTrails.show(player, points);
        player.sendMessage(Text.translatable("mazecraft.thread.distance", (int) distance, compass(dir))
                .formatted(Formatting.GOLD), true);
        return true;
    }

    private static Text compass(Vec3d dir) {
        double angle = Math.toDegrees(Math.atan2(dir.x, -dir.z)); // 0 = north, 90 = east
        String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        return Text.translatable("mazecraft.thread.dir." + keys[index]);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mazecraft.ariadne_thread.tooltip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.mazecraft.ariadne_thread.tooltip2").formatted(Formatting.GRAY));
    }
}
