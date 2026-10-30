package fr.mazecraft.progression;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Fireworks over a place where something has just been won.
 *
 * <p>Lifted out of {@code KronosArena}, which had it as a private method, because the crypt at
 * the end of a cherry maze wants the same moment in its own colours. The two things that have to
 * be arguments are the ones that differ: the colours, and how high the rockets go — the arena of
 * Kronos is open to a nineteen-block ceiling and a crypt is seven blocks tall, where a rocket
 * with two seconds of flight detonates in the roof.</p>
 */
public final class Fanfare {

    private Fanfare() { }

    /**
     * @param ring   how far out from the middle the rockets are set off
     * @param flight rocket flight duration: 1 in a low room, 2 under a high ceiling
     */
    public static void burst(ServerWorld world, BlockPos centre, double ring, int flight,
                             int primary, int secondary) {
        for (int i = 0; i < 12; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(flight, List.of(
                    new FireworkExplosionComponent(
                            i % 2 == 0 ? FireworkExplosionComponent.Type.LARGE_BALL
                                       : FireworkExplosionComponent.Type.STAR,
                            IntList.of(primary, secondary),
                            IntList.of(0xFFFFFF),
                            true, true))));
            double angle = Math.PI * 2 * i / 12.0;
            world.spawnEntity(new FireworkRocketEntity(world,
                    centre.getX() + 0.5 + Math.cos(angle) * ring,
                    centre.getY() - 1.0,
                    centre.getZ() + 0.5 + Math.sin(angle) * ring,
                    rocket));
        }
        world.playSound(null, centre, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, centre, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER,
                SoundCategory.BLOCKS, 2.0f, 0.5f);
    }
}
