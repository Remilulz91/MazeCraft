package fr.mazecraft.mixin;

import fr.mazecraft.structure.MazeFinder;
import net.minecraft.block.BlockState;
import net.minecraft.block.VineBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vines don't spread inside a maze (random ticks), so vines growing on a nearby tree can't
 * creep onto the walls and become a ladder over them.
 */
@Mixin(VineBlock.class)
public abstract class VineBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mazecraft$noSpreadInMaze(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (MazeFinder.find(world, pos, 0) != null) {
            ci.cancel();
        }
    }
}
