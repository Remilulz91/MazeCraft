package fr.mazecraft;

import fr.mazecraft.commands.MazeCommand;
import fr.mazecraft.config.MazeCraftConfig;
import fr.mazecraft.protection.MazeProtection;
import fr.mazecraft.structure.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main entry point of MazeCraft.
 * Initializes all server-side and common-side systems.
 */
public class MazeCraft implements ModInitializer {

    public static final String MOD_ID = "mazecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("MazeCraft");

    // === Build type detection (set by Gradle's processResources) ===
    private static final boolean IS_DEBUG_BUILD;
    private static final String VERSION;
    static {
        boolean debug = false;
        String version = "unknown";
        try (InputStream is = MazeCraft.class.getResourceAsStream("/mazecraft.build.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                debug = "debug".equalsIgnoreCase(p.getProperty("build.type", "public").trim());
                version = p.getProperty("version", "unknown").trim();
            }
        } catch (IOException ignored) { }
        IS_DEBUG_BUILD = debug;
        VERSION = version;
    }

    /** Returns true if this JAR was built as the debug variant. */
    public static boolean isDebugBuild() {
        return IS_DEBUG_BUILD;
    }

    /** Returns the mod version baked in at build time. */
    public static String getVersion() {
        return VERSION;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("==============================================");
        LOGGER.info("    MazeCraft v{} - Starting up ({} build)",
                VERSION, IS_DEBUG_BUILD ? "DEBUG" : "PUBLIC");
        LOGGER.info("==============================================");

        // 1. Load configuration
        MazeCraftConfig.load();
        LOGGER.info("[MazeCraft] Configuration loaded");

        // 2. Register the maze structure type + piece type (before datapacks load)
        ModStructures.register();

        // 3. Register commands (/maze version, /maze reload, /maze debug ...)
        CommandRegistrationCallback.EVENT.register(MazeCommand::register);
        LOGGER.info("[MazeCraft] Commands registered");

        // 4. Anti-cheat protection + "maze conquered" detection (central chest)
        MazeProtection.register();

        // Next step: lever / gate blocks.

        LOGGER.info("[MazeCraft] Mod loaded successfully!");
    }

    /**
     * Creates an Identifier in the mod's namespace.
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
