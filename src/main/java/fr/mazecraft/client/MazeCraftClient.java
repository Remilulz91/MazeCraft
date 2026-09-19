package fr.mazecraft.client;

import fr.mazecraft.MazeCraft;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side entry point.
 * Client-only registrations (block render layers, entity renderers...) will go here.
 */
public class MazeCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MazeCraft.LOGGER.info("[MazeCraft] Client initialized.");
    }
}
