package fr.mazecraft.structure;

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

    // === Progression (since 0.8.0) ===
    //
    // Each style generates three mazes — one per PROGRESSION step — and each step has its
    // own structure and its own structure set, so a given style/size pair is always findable
    // (see data/mazecraft/worldgen/structure[_set]/maze_<style>_<size>.json).
    // COLOSSAL is not a step of its own: it is a rare variant of LARGE and counts as LARGE.

    /** The three progression steps, in order. COLOSSAL is excluded (it counts as LARGE). */
    public static final MazeSize[] STEPS = { SMALL, MEDIUM, LARGE };

    /** The step this size belongs to: COLOSSAL counts as LARGE, every other size is its own step. */
    public MazeSize step() {
        return this == COLOSSAL ? LARGE : this;
    }

    /** The step that must be cleared before this one, or null for the first step. */
    public MazeSize previousStep() {
        return switch (step()) {
            case SMALL -> null;
            case MEDIUM -> SMALL;
            default -> MEDIUM;
        };
    }

    /** 1-based index of this size's step (SMALL 1, MEDIUM 2, LARGE/COLOSSAL 3). */
    public int stepIndex() {
        return step().ordinal() + 1;
    }

    /** One LARGE maze in {@code 1/CHANCE} is upgraded to a colossal one (Overworld and End only). */
    public static final int COLOSSAL_CHANCE = 8;

    /**
     * Relief the terrain may have across the footprint before the spot is rejected.
     *
     * <p>0.8.0 pushed this up to 18–20 for the big sizes, because a structure can no longer fall
     * back to a smaller one and large mazes were getting hard to find. In game that showed: a
     * flat 149-block platform allowed to sit on 18 blocks of relief cuts a hillside into a
     * visible escarpment. The tolerance is back near its pre-0.8.0 value and the rarity is paid
     * for with tighter spacing instead — density is the knob that does not hurt the look.</p>
     */
    public int maxRelief() {
        return switch (this) { case SMALL -> 10; case MEDIUM -> 12; case LARGE -> 14; case COLOSSAL -> 16; };
    }
}
