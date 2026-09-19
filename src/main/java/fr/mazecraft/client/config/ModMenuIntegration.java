package fr.mazecraft.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu integration: adds a config button to the mod list.
 * The screen itself lives in {@link MazeCraftConfigScreen} so that Cloth Config
 * classes are only loaded when Cloth Config is actually installed (it is optional).
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return MazeCraftConfigScreen::create;
    }
}
