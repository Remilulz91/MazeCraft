package fr.mazecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.mazecraft.MazeCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Central configuration for MazeCraft.
 * Saved to config/mazecraft.json.
 */
public class MazeCraftConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("mazecraft.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static MazeCraftConfig INSTANCE = new MazeCraftConfig();

    // === Maze settings ===
    // (added step by step as features land: sizes, protection, loot...)

    // === DEBUG (disabled by default in public builds, enabled in debug builds) ===

    public boolean enableDebugCommands = MazeCraft.isDebugBuild();

    // === Methods ===

    public static MazeCraftConfig get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                MazeCraftConfig loaded = GSON.fromJson(json, MazeCraftConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
                MazeCraft.LOGGER.info("[Config] Configuration loaded from {}", CONFIG_PATH);
            } else {
                save();
                MazeCraft.LOGGER.info("[Config] Default configuration created");
            }
        } catch (Exception e) {
            // Catches IOException and malformed JSON: keep defaults instead of crashing
            MazeCraft.LOGGER.error("[Config] Loading error, using defaults: {}", e.getMessage());
        }

        // SECURITY: PUBLIC builds force debug flags to false regardless of config file.
        if (!MazeCraft.isDebugBuild() && INSTANCE.enableDebugCommands) {
            INSTANCE.enableDebugCommands = false;
            MazeCraft.LOGGER.warn("[Config] ⚠ Debug flags found in config file but this is a PUBLIC build —");
            MazeCraft.LOGGER.warn("[Config] ⚠ they are IGNORED. To use debug features, install the DEBUG build.");
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            MazeCraft.LOGGER.error("[Config] Save error: {}", e.getMessage());
        }
    }
}
