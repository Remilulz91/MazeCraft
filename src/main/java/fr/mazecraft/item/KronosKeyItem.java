package fr.mazecraft.item;

import fr.mazecraft.block.SealedGatewayBlock;
import fr.mazecraft.MazeCraft;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import fr.mazecraft.progression.MazeProgress;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
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

import java.util.List;
import java.util.UUID;

/**
 * The Key of Kronos: granted to a player the moment they complete the sixteenth style.
 *
 * <p>It is not crafted. A recipe cannot tell one key fragment from another — they are the same
 * item carrying a different style in its data — so a crafting grid would happily accept sixteen
 * copies of the same fragment and hand out a key for a sixth of the work. The fragments stay
 * what they are, a collection; the key arrives with the last of them.</p>
 *
 * <p>It is bound to the player who earned it. Handing it over gets the other player nothing:
 * the key stays inert in their hands, and the door of Kronos reads their own progression
 * anyway. Its one use is to point the way — the vault is buried forty blocks down and would
 * otherwise never be found.</p>
 */
public class KronosKeyItem extends Item {

    /**
     * Radius of ONE sweep, in rings of the Kronos set's spread grid — not in chunks. See
     * {@link StructureSearch}.
     *
     * <p>This was 200, with the comment "generous: there is one of these in a very large area".
     * It was not generous, it was a loaded gun: the Kronos set is spaced <b>200 chunks</b>
     * apart, so 200 rings meant 160,801 candidate sites and 640,000 blocks swept inside a
     * single tick — four times worse than the compass that killed the server twice, in the one
     * item every player eventually holds. Nobody had reached the endgame on a server yet, which
     * is the only reason it had not crashed there too.</p>
     *
     * <p>4 rings is 81 sites and 12,800 blocks a sweep. Nine sweeps comb 30,000 blocks, which
     * for a structure this rare is the right reach — bought one affordable tick at a time.</p>
     */
    private static final int SEARCH_RADIUS = 4;

    private static final int COOLDOWN = 100;

    /** Which sweep the next use runs, stored on the stack so the search can be resumed. */
    private static final String SWEEP_KEY = "KronosSweep";

    public KronosKeyItem(Settings settings) {
        super(settings);
    }

    /**
     * The advancement for having turned a key in a door of Kronos.
     *
     * <p>A trophy now, not a permit. It used to be what let a player through — the door was
     * open for whoever held the advancement and shut for everyone else, which is the only way
     * to gate a block per player. The key is crafted from nine fragments rather than handed to
     * the one who earned it, so there is no "earned it" left to check: turning it opens the
     * door for the server, and the record of who did it is just a record.</p>
     */
    public static final String UNSEALED = "unseal_kronos";

    /**
     * Turning the key in the door of Kronos.
     *
     * <p>Until now the door read the player's advancements directly and the key only pointed at
     * the vault, which made it a souvenir: everything it was for had already happened by the
     * time you held it. Now it is the act itself — the door opens because the key is turned in
     * it, and the key is spent doing so.</p>
     *
     * <p>Spending it is only safe because finding Kronos has moved to the compass, which keeps
     * working for ever. A key that both opened the door and was the only way to find the place
     * again would have been a trap the moment it was consumed.</p>
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;

        BlockState state = world.getBlockState(context.getBlockPos());
        if (!(state.getBlock() instanceof SealedGatewayBlock)
                || state.get(SealedGatewayBlock.TIER) != SealedGatewayBlock.Tier.KRONOS) {
            return ActionResult.PASS;
        }

        BlockPos at = context.getBlockPos();
        MazeFinder.MazeHit hit = MazeFinder.find(serverWorld, at, 2);
        if (hit == null) return ActionResult.PASS;

        // Per maze, not per player. There is more than one Labyrinth of Kronos in a world, so
        // this door being open says nothing about the next one — which is what makes a second
        // key worth crafting.
        if (!MazeState.get(serverWorld).openKronos(hit.key())) {
            player.sendMessage(Text.translatable("mazecraft.kronos.already_open").formatted(Formatting.GRAY), true);
            return ActionResult.FAIL;
        }

        // The whole plane, not the block that was clicked: a door with one square missing is
        // not an open door, it is a hole.
        for (BlockPos pane : hit.piece().entranceBlocks()) {
            if (serverWorld.getBlockState(pane).getBlock() instanceof SealedGatewayBlock) {
                serverWorld.setBlockState(pane, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        MazeProgress.grant(player, UNSEALED);
        context.getStack().decrement(1);

        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 1.5,
                at.getZ() + 0.5, 80, 0.8, 1.4, 0.8, 0.04);
        serverWorld.spawnParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.5,
                at.getZ() + 0.5, 40, 0.6, 1.2, 0.6, 0.08);
        world.playSound(null, at, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.6f, 1.4f);
        world.playSound(null, at, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.4f, 0.5f);
        // Everyone within earshot, because the door just opened for all of them.
        for (ServerPlayerEntity nearby : serverWorld.getPlayers()) {
            if (nearby.getBlockPos().isWithinDistance(at, 64)) {
                nearby.sendMessage(Text.translatable("mazecraft.kronos.unsealed")
                        .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient());
        }

        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            player.sendMessage(Text.translatable("mazecraft.kronos.wrong_dimension").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }

        user.getItemCooldownManager().set(this, COOLDOWN);
        TagKey<Structure> tag = TagKey.of(RegistryKeys.STRUCTURE, MazeCraft.id("kronos"));
        int sweep = StructureSearch.storedSweep(stack, SWEEP_KEY);
        BlockPos found = StructureSearch.locate(serverWorld, tag,
                StructureSearch.sweepOrigin(user.getBlockPos(), sweep,
                        StructureSearch.reach(serverWorld, tag, SEARCH_RADIUS)),
                SEARCH_RADIUS, "kronos");
        if (found == null) {
            // One sweep per use, so a miss is not an answer yet — it is a reason to look at the
            // next patch of ground. Only the ninth miss means there is really nothing here.
            if (sweep < StructureSearch.LAST_SWEEP) {
                StructureSearch.storeSweep(stack, SWEEP_KEY, sweep + 1);
                player.sendMessage(Text.translatable("mazecraft.kronos.sweeping",
                        Text.literal(String.valueOf(sweep + 1)).formatted(Formatting.WHITE),
                        Text.literal(String.valueOf(StructureSearch.LAST_SWEEP + 1)).formatted(Formatting.WHITE))
                        .formatted(Formatting.GRAY), false);
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        SoundCategory.PLAYERS, 0.5f, 1.2f);
                return TypedActionResult.success(stack, false);
            }
            StructureSearch.storeSweep(stack, SWEEP_KEY, 0);
            player.sendMessage(Text.translatable("mazecraft.kronos.not_found").formatted(Formatting.GRAY), false);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            return TypedActionResult.success(stack, false);
        }
        StructureSearch.storeSweep(stack, SWEEP_KEY, 0);

        int distance = (int) Math.sqrt(user.getBlockPos().getSquaredDistance(
                new BlockPos(found.getX(), user.getBlockY(), found.getZ())));
        player.sendMessage(Text.translatable("mazecraft.kronos.found",
                Text.literal(String.valueOf(distance)).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getX())).formatted(Formatting.WHITE),
                Text.literal(String.valueOf(found.getZ())).formatted(Formatting.WHITE))
                .formatted(Formatting.LIGHT_PURPLE), false);
        world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, 0.7f, 1.4f);

        Vec3d origin = user.getPos().add(0, 1.2, 0);
        Vec3d direction = new Vec3d(found.getX() - origin.x, 0, found.getZ() - origin.z).normalize();
        for (int i = 1; i <= 16; i++) {
            Vec3d p = origin.add(direction.multiply(i * 0.8));
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("mazecraft.kronos.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
