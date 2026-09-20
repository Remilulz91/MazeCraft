package fr.mazecraft.item;

import fr.mazecraft.entity.ModSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Minotaur Horn — trophy dropped by the Minotaur. Blowing it gives Strength I and Speed I
 * for 30 s to every player within 16 blocks (the whole team exploring a maze). 2 min cooldown.
 */
public class MinotaurHornItem extends Item {

    private static final int DURATION = 30 * 20;
    private static final int COOLDOWN = 120 * 20;
    private static final double RANGE = 16.0;

    public MinotaurHornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld server) {
            server.playSound(null, user.getBlockPos(), ModSounds.MINOTAUR_HORN, SoundCategory.PLAYERS, 3.0f, 0.8f);
            for (PlayerEntity player : server.getPlayers(p -> p.squaredDistanceTo(user) <= RANGE * RANGE)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, DURATION, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DURATION, 0));
            }
            user.getItemCooldownManager().set(this, COOLDOWN);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mazecraft.minotaur_horn.tooltip").formatted(Formatting.GRAY));
    }
}
