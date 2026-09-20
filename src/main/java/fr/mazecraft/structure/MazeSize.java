package fr.mazecraft.structure;

import fr.mazecraft.config.MazeCraftConfig;
import net.minecraft.util.math.random.Random;

import java.util.Locale;

/**
 * Maze sizes. A maze is a square grid of {@code cells × cells} cells;
 * each cell is a 3-block corridor + a 1-block wall, so the footprint is
 * {@code cells * 4 + 1} blocks per side.
 *
 * Cell counts are odd so the maze always has a true center cell.
 */
public enum MazeSize {
    SMALL(15, 2),     //  61 ×  61 blocks, 2 gates
    MEDIUM(25, 3),    // 101 × 101 blocks, 3 gates
    LARGE(37, 4),     // 149 × 149 blocks, 4 gates
    COLOSSAL(55, 5);  // 221 × 221 blocks, 5 gates — max: half-span 110 + ring (≤ MAX_MARGIN 8) + 8 must stay < 128 (8-chunk structure reach)

    private final int cells;
    private final int gates;

    MazeSize(int cells, int gates) {
        this.cells = cells;
        this.gates = gates;
    }

    /** Number of gates (and levers) on the way to the center; the last gate is the plaza door. */
    public int gates() {
        return gates;
    }

    public int cells() {
        return cells;
    }

    /** Side length of the maze footprint in blocks. */
    public int span() {
        return cells * MazeLayout.CELL + 1;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    // === Difficulty (enemies) ===

    /** Armor tier of enemies, from the first zone (progress 0) to the last one (progress 1). 0 none, 1 leather, 2 iron, 3 diamond. */
    public int enemyTier(double progress) {
        int min = switch (this) { case SMALL, MEDIUM -> 0; case LARGE -> 1; case COLOSSAL -> 2; };
        int max = switch (this) { case SMALL -> 1; case MEDIUM, LARGE -> 2; case COLOSSAL -> 3; };
        return min + (int) Math.round((max - min) * progress);
    }

    /** Ambush size (before the config multiplier) from the first lever (progress 0) to the last one. */
    public int ambushCount(double progress) {
        int min = switch (this) { case SMALL -> 1; case MEDIUM, LARGE -> 2; case COLOSSAL -> 3; };
        int max = switch (this) { case SMALL -> 3; case MEDIUM -> 4; case LARGE -> 5; case COLOSSAL -> 6; };
        return min + (int) Math.round((max - min) * progress);
    }

    /** Champion health multiplier. */
    public double championHealth() {
        return switch (this) { case SMALL -> 2.0; case MEDIUM -> 2.5; case LARGE -> 3.0; case COLOSSAL -> 4.0; };
    }

    /** Champion gear: iron in small mazes, diamond otherwise; enchantment level grows with size. */
    public boolean championDiamond() {
        return this != SMALL;
    }

    public int championEnchantLevel() {
        return switch (this) { case SMALL -> 5; case MEDIUM -> 12; case LARGE -> 20; case COLOSSAL -> 30; };
    }

    /** Next smaller size, or null for SMALL. Used when the terrain is too uneven for this size. */
    public MazeSize smaller() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    public static MazeSize fromId(String id) {
        for (MazeSize s : values()) {
            if (s.id().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    /** Weight of this size in the random roll, read from the config. */
    private int weight() {
        MazeCraftConfig cfg = MazeCraftConfig.get();
        return Math.max(0, switch (this) {
            case SMALL -> cfg.weightSmall;
            case MEDIUM -> cfg.weightMedium;
            case LARGE -> cfg.weightLarge;
            case COLOSSAL -> cfg.weightColossal;
        });
    }

    /** Rolls a random size using the configured weights (falls back to SMALL if all weights are 0). */
    /** End: the ultimate mazes, bigger on average (small 20 / medium 30 / large 30 / colossal 20). */
    public static MazeSize rollEnd(Random random) {
        int r = random.nextInt(100);
        if (r < 20) return SMALL;
        if (r < 50) return MEDIUM;
        if (r < 80) return LARGE;
        return COLOSSAL;
    }

    public static MazeSize roll(Random random) {
        int total = 0;
        for (MazeSize s : values()) total += s.weight();
        if (total <= 0) return SMALL;
        int r = random.nextInt(total);
        for (MazeSize s : values()) {
            r -= s.weight();
            if (r < 0) return s;
        }
        return SMALL;
    }
}
