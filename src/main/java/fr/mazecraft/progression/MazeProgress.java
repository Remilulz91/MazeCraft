package fr.mazecraft.progression;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStyle;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player maze progression (since 0.8.0).
 *
 * <p>Each style must be cleared one step at a time: small → medium → large. A step counts as
 * cleared once the player has opened the central chest of a maze of that style and size.</p>
 *
 * <p>The source of truth is the player's own advancement tracker
 * ({@code mazecraft:conquer_<style>_<size>}), not a side table. That is deliberate:</p>
 * <ul>
 *   <li>advancements are already per-player, already saved, and already synced to the client,
 *       so progression works the same in singleplayer and on a server, across dimensions;</li>
 *   <li>the advancement tree the player reads in the menu <em>is</em> the progression — the two
 *       can never disagree;</li>
 *   <li>nothing extra to migrate when the format changes.</li>
 * </ul>
 *
 * <p>The trade-off: an operator who grants the advancement by hand also grants the progression.
 * That is acceptable — it is the same level of trust as {@code /gamemode creative}.</p>
 */
public final class MazeProgress {

    /** Number of steps to clear before the Labyrinth of Kronos opens (16 styles × 3 steps). */
    public static final int TOTAL_STEPS = MazeStyle.values().length * MazeSize.STEPS.length;

    private MazeProgress() { }

    /** Advancement granted when a maze of this style and size is conquered. */
    public static String advancementId(MazeStyle style, MazeSize size) {
        return "conquer_" + style.id() + "_" + size.step().id();
    }

    public static boolean hasAdvancement(ServerPlayerEntity player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        AdvancementEntry entry = server.getAdvancementLoader().get(MazeCraft.id(id));
        return entry != null && player.getAdvancementTracker().getProgress(entry).isDone();
    }

    /** Grants every remaining criterion of an advancement. */
    public static void grant(ServerPlayerEntity player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementEntry entry = server.getAdvancementLoader().get(MazeCraft.id(id));
        if (entry == null) return;
        for (String criterion : player.getAdvancementTracker().getProgress(entry).getUnobtainedCriteria()) {
            player.getAdvancementTracker().grantCriterion(entry, criterion);
        }
    }

    /** Has the player already conquered a maze of this style at this step? */
    public static boolean hasCleared(ServerPlayerEntity player, MazeStyle style, MazeSize size) {
        return hasAdvancement(player, advancementId(style, size));
    }

    /**
     * May the player enter a maze of this style and size? True when the previous step of the
     * <em>same</em> style has been cleared — progression never carries over between styles.
     */
    public static boolean canEnter(ServerPlayerEntity player, MazeStyle style, MazeSize size) {
        MazeSize previous = size.previousStep();
        return previous == null || hasCleared(player, style, previous);
    }

    /** The step this player must clear next for this style, or null if the style is complete. */
    public static MazeSize nextStep(ServerPlayerEntity player, MazeStyle style) {
        for (MazeSize step : MazeSize.STEPS) {
            if (!hasCleared(player, style, step)) return step;
        }
        return null;
    }

    public static boolean isStyleComplete(ServerPlayerEntity player, MazeStyle style) {
        return nextStep(player, style) == null;
    }

    /** How many of the {@link #TOTAL_STEPS} steps this player has cleared. */
    public static int completedSteps(ServerPlayerEntity player) {
        int done = 0;
        for (MazeStyle style : MazeStyle.values()) {
            for (MazeSize step : MazeSize.STEPS) {
                if (hasCleared(player, style, step)) done++;
            }
        }
        return done;
    }

    /** True once every style of every dimension is complete — the key to Kronos (1.0.0). */
    public static boolean isEverythingComplete(ServerPlayerEntity player) {
        return completedSteps(player) >= TOTAL_STEPS;
    }

    // === Dimensions ===

    /** The styles that generate in the given dimension. */
    public static List<MazeStyle> stylesOf(World world) {
        var key = world.getRegistryKey();
        List<MazeStyle> list = new ArrayList<>();
        for (MazeStyle style : MazeStyle.values()) {
            boolean match;
            if (key.equals(World.NETHER)) match = style.enclosed;
            else if (key.equals(World.END)) match = style.isEnd();
            else if (key.equals(World.OVERWORLD)) match = !style.enclosed && !style.isEnd();
            else match = false; // modded dimension: no maze of ours
            if (match) list.add(style);
        }
        return list;
    }
}
