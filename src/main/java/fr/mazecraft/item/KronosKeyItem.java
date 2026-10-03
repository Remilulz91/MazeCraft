package fr.mazecraft.item;

import fr.mazecraft.block.SealedGatewayBlock;
import fr.mazecraft.MazeCraft;
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

    /** Search radius, in chunks. Generous: there is one of these in a very large area. */
    private static final int SEARCH_RADIUS = 200;

    private static final int COOLDOWN = 100;

    private static final String OWNER_KEY = "KronosOwner";

    public KronosKeyItem(Settings settings) {
        super(settings);
    }

    /** A key bound to this player. */
    public static ItemStack forPlayer(Item item, PlayerEntity owner) {
        ItemStack stack = new ItemStack(item);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(OWNER_KEY, owner.getUuid().toString());
        nbt.putString("OwnerName", owner.getGameProfile().getName());
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, current -> {
            NbtCompound merged = current.copyNbt();
            merged.copyFrom(nbt);
            return NbtComponent.of(merged);
        });
        return stack;
    }

    private static String ownerName(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? "" : data.copyNbt().getString("OwnerName");
    }

    private static boolean isOwner(ItemStack stack, PlayerEntity player) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return false;
        String owner = data.copyNbt().getString(OWNER_KEY);
        try {
            return !owner.isEmpty() && UUID.fromString(owner).equals(player.getUuid());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The advancement that records, per player, that this door has been opened for them. */
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

        ItemStack stack = context.getStack();
        if (!isOwner(stack, player)) {
            player.sendMessage(Text.translatable("mazecraft.kronos.not_yours").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }
        if (!MazeProgress.isEverythingComplete(player)) {
            player.sendMessage(Text.translatable("mazecraft.barrier.kronos",
                    MazeProgress.completedSteps(player), MazeProgress.TOTAL_STEPS)
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }
        if (MazeProgress.hasAdvancement(player, UNSEALED)) {
            player.sendMessage(Text.translatable("mazecraft.kronos.already_open").formatted(Formatting.GRAY), true);
            return ActionResult.FAIL;
        }

        MazeProgress.grant(player, UNSEALED);
        stack.decrement(1);

        BlockPos at = context.getBlockPos();
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 1.5,
                at.getZ() + 0.5, 80, 0.8, 1.4, 0.8, 0.04);
        serverWorld.spawnParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.5,
                at.getZ() + 0.5, 40, 0.6, 1.2, 0.6, 0.08);
        world.playSound(null, at, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.6f, 1.4f);
        world.playSound(null, at, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.4f, 0.5f);
        player.sendMessage(Text.translatable("mazecraft.kronos.unsealed")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient());
        }

        if (!isOwner(stack, player)) {
            player.sendMessage(Text.translatable("mazecraft.kronos.not_yours").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            player.sendMessage(Text.translatable("mazecraft.kronos.wrong_dimension").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }

        user.getItemCooldownManager().set(this, COOLDOWN);
        TagKey<Structure> tag = TagKey.of(RegistryKeys.STRUCTURE, MazeCraft.id("kronos"));
        BlockPos found = serverWorld.locateStructure(tag, user.getBlockPos(), SEARCH_RADIUS, false);
        if (found == null) {
            player.sendMessage(Text.translatable("mazecraft.kronos.not_found").formatted(Formatting.GRAY), false);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            return TypedActionResult.success(stack, false);
        }

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
        String owner = ownerName(stack);
        if (!owner.isEmpty()) {
            tooltip.add(Text.translatable("mazecraft.kronos.bound", owner).formatted(Formatting.LIGHT_PURPLE));
        }
        tooltip.add(Text.translatable("mazecraft.kronos.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
