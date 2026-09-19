package fr.mazecraft.mixin;

import fr.mazecraft.protection.MazeProtection;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An ender pearl thrown by a survival/adventure player does not teleport when it lands in,
 * or is thrown from, an unconquered maze: the pearl is simply consumed.
 */
@Mixin(EnderPearlEntity.class)
public abstract class EnderPearlEntityMixin {

    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void mazecraft$noPearlShortcut(HitResult hitResult, CallbackInfo ci) {
        EnderPearlEntity self = (EnderPearlEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) return;
        if (!(self.getOwner() instanceof ServerPlayerEntity player) || player.isCreative() || player.isSpectator()) return;

        BlockPos landing = BlockPos.ofFloored(hitResult.getPos());
        if (MazeProtection.isInLockedMaze(world, landing) || MazeProtection.isInLockedMaze(world, player.getBlockPos())) {
            player.sendMessage(Text.translatable("mazecraft.protection.no_pearl").formatted(Formatting.RED), true);
            self.discard();
            ci.cancel();
        }
    }
}
