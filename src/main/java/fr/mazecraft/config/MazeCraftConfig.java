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

    // === Maze generation ===
    // Relative weights of each maze size when a maze is generated in the world.
    // Example: 45/35/15/5 → 45% small, 35% medium, 15% large, 5% colossal.
    // Only affects mazes generated AFTER the change (existing mazes keep their size).

    public int weightSmall = 45;
    public int weightMedium = 35;
    public int weightLarge = 15;
    public int weightColossal = 5;

    // === Protection (until the central chest is opened) ===

    /** Walls can't be broken and blocks can't be placed inside an unconquered maze. */
    public boolean protectUntilSolved = true;

    /** Players standing on / flying over the walls of an unconquered maze are sent back to the entrance. */
    public boolean preventWallWalking = true;

    // === Enemies ===

    /** Mobs placed in the maze when it generates (persistent). */
    public boolean enableGuardians = true;

    /** Pulling a lever spawns a wave of mobs around the player. */
    public boolean enableAmbushes = true;

    /** The last lever also spawns a champion (named, 4x health, enchanted gear, boss bar). */
    public boolean enableChampion = true;

    /** Patrols: while a player explores an unconquered maze, mobs keep appearing out of sight (day and night). */
    public boolean enablePatrols = true;

    /** Seconds between two patrol mobs in a small maze (medium ×0.75, large ×0.6, colossal ×0.5). */
    public int patrolIntervalSeconds = 30;

    /** Multiplier on the number of guardians and ambush mobs (0 = none, 1 = default, 2 = double). */
    public double enemyMultiplier = 1.0;

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
                save(); // rewrite so options added in newer versions appear in the file
                MazeCraft.LOGGER.info("[Config] Configuration loaded from {}", CONFIG_PATH);
            } else {
                save();
                MazeCraft.LOGGER.info("[Config] Default configuration created");
            }
        } catch (Exception e) {
            // Catches IOException and malformed JSON: keep defaults instead of crashing
            MazeCraft.LOGGER.error("[Config] Loading error, using defaults: {}", e.getMessage());
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
