package fr.mazecraft.client;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.block.ModBlocks;
import fr.mazecraft.client.render.MinotaurModel;
import fr.mazecraft.client.render.MinotaurRenderer;
import fr.mazecraft.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-side entry point: entity renderers and model layers.
 */
public class MazeCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The sealed gateway is a translucent membrane, like a nether portal.
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SEALED_GATEWAY, RenderLayer.getTranslucent());

        EntityModelLayerRegistry.registerModelLayer(MinotaurModel.LAYER, MinotaurModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.MINOTAUR, MinotaurRenderer::new);
        MazeCraft.LOGGER.info("[MazeCraft] Client initialized.");
    }
}
