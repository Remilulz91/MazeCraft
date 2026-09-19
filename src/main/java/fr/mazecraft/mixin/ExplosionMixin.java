package fr.mazecraft.mixin;

import fr.mazecraft.protection.MazeProtection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Explosions (creepers, TNT, ghast fireballs...) don't destroy blocks of an unconquered maze.
 * Entities are still damaged normally.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow @Final private World world;

    @Shadow public abstract List<BlockPos> getAffectedBlocks();

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("TAIL"))
    private void mazecraft$protectMazeBlocks(CallbackInfo ci) {
        if (this.world instanceof ServerWorld serverWorld) {
            MazeProtection.filterExplosion(serverWorld, getAffectedBlocks());
        }
    }
}
