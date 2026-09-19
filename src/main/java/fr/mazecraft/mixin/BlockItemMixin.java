package fr.mazecraft.mixin;

import fr.mazecraft.protection.MazeProtection;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks placing any block inside a maze that hasn't been conquered yet.
 * Hooked on BlockItem#place so it covers every block item (scaffolding, ladders...)
 * without interfering with right-clicking chests or levers while holding a block.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true)
    private void mazecraft$preventPlacementInMaze(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getWorld() instanceof ServerWorld world
                && !MazeProtection.canPlace(world, context.getPlayer(), context.getBlockPos())) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
