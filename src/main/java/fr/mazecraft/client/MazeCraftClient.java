package fr.mazecraft.client;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.client.render.MinotaurModel;
import fr.mazecraft.client.render.MinotaurRenderer;
import fr.mazecraft.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client-side entry point: entity renderers and model layers.
 */
public class MazeCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(MinotaurModel.LAYER, MinotaurModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.MINOTAUR, MinotaurRenderer::new);
        MazeCraft.LOGGER.info("[MazeCraft] Client initialized.");
    }
}
