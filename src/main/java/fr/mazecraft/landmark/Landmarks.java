package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeStyle;

/**
 * Which style is reached through which building.
 *
 * <p>The one file a new landmark has to touch, and it is three lines. Everything else about a
 * building — its template, its staircase, the ground it needs, how deep its maze goes — lives in
 * its own class, so adding the desert pyramid cannot reach the pagoda.</p>
 */
public final class Landmarks {

    private Landmarks() { }

    /** The landmark this style is entered through, or null when it has none. */
    public static Landmark of(MazeStyle style) {
        if (style == CherryPagoda.STYLE) return CherryPagoda.INSTANCE;
        return null;
    }

    /** True when this style's maze is buried under a building rather than placed on the surface. */
    public static boolean has(MazeStyle style) {
        return of(style) != null;
    }
}
