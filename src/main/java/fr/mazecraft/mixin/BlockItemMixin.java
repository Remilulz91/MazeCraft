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
        // The block comes from the stack, not from the world: at HEAD nothing has been placed
        // yet, so there is no state to read, and a torch put against a wall becomes a different
        // block from the one in the hand. The item knows what it is; the position does not.
        net.minecraft.block.Block placing = context.getStack().getItem() instanceof BlockItem item
                ? item.getBlock() : null;
        if (context.getWorld() instanceof ServerWorld world
                && !MazeProtection.canPlace(world, context.getPlayer(), context.getBlockPos(), placing)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
