package fr.mazecraft.mixin;

import fr.mazecraft.structure.ModStructures;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.VinesFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * No natural vines in chunks crossed by a maze. A maze can straddle a biome border
 * (e.g. badlands next to a jungle): the jungle "vines" feature then covered the walls.
 * During world generation only the chunk's structure references are reliable, so the whole
 * chunk is skipped (vines are purely decorative).
 */
@Mixin(VinesFeature.class)
public abstract class VinesFeatureMixin {

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void mazecraft$noVinesOnMazes(FeatureContext<DefaultFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
        Chunk chunk = context.getWorld().getChunk(context.getOrigin());
        for (Map.Entry<Structure, LongSet> entry : chunk.getStructureReferences().entrySet()) {
            if (entry.getKey().getType() == ModStructures.MAZE && !entry.getValue().isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
