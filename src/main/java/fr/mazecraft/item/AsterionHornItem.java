package fr.mazecraft.item;

import fr.mazecraft.entity.ModSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Horn of Asterion — the one thing carried out of the Labyrinth of Kronos.
 *
 * <p>The Bronze Horn of a large maze is a good item; this is the same gesture at the far end of
 * the game, so it has to be plainly better rather than slightly better: Strength II, Speed II
 * and Resistance I for two minutes, over twice the range, and no cooldown worth the name by
 * comparison. It is the reward for 48 mazes and a 400 HP boss — a 10 % improvement would read
 * as an insult.</p>
 */
public class AsterionHornItem extends Item {

    private static final int DURATION = 120 * 20;
    private static final int COOLDOWN = 180 * 20;
    private static final double RANGE = 40.0;

    public AsterionHornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld server) {
            server.playSound(null, user.getBlockPos(), ModSounds.ASTERION_HORN, SoundCategory.PLAYERS, 4.0f, 1.0f);
            for (PlayerEntity player : server.getPlayers(p -> p.squaredDistanceTo(user) <= RANGE * RANGE)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, DURATION, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DURATION, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, DURATION, 0));
                server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0,
                        player.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            }
            user.getItemCooldownManager().set(this, COOLDOWN);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mazecraft.asterion_horn.tooltip").formatted(Formatting.GRAY));
    }
}
