package fr.mazecraft.landmark;

import fr.mazecraft.structure.MazeSize;
import fr.mazecraft.structure.MazeStructure;
import fr.mazecraft.structure.MazeStyle;
import fr.mazecraft.structure.MazeTemplates;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.OptionalInt;

/**
 * A building on the surface that is the way into a maze buried under it.
 *
 * <p>This package is the seam. Everything that is genuinely particular to one biome's maze lives
 * in its own file here — the building, the staircase down it, what ground it needs, how deep the
 * maze goes — so that working on the cherry one cannot reach the desert one. What is <b>not</b>
 * here is the maze itself: {@code MazeLayout} and the renderer in {@code MazePiece} stay shared,
 * because they are the one part of the mod that is proved rather than looked at (two hundred
 * thousand terrains, no divergence) and sixteen copies of a proof is sixteen chances to fix a bug
 * once and forget it fifteen times. The split protects the styles from each other exactly where
 * they differ, and shares exactly what they do not.</p>
 *
 * <p>{@code MazeStructure} gains one generic branch — "does this style have a landmark? then ask
 * it" — so the next building is a new file and nothing else touched.</p>
 */
public interface Landmark {

    /**
     * The style whose mazes this building is the way into.
     *
     * <p>Also this landmark's name on disk. A piece saved in a chunk has to find its way back to
     * the building it belongs to when the chunk is read again, and the style is the one thing
     * that is already unique per landmark and already has a stable id.</p>
     */
    MazeStyle style();

    /** The template placed on the surface. */
    MazeTemplates.Piece piece();

    /** Height of the template, for the piece's bounding box. */
    int templateHeight();

    /** Where the way down is cut through the building, and what it is made of. */
    Descent descent();

    /**
     * A block in the template that stands for "whatever the ground is here", or null.
     *
     * <p>A template carries one ground: the badlands starter house is built on red sand, and a
     * badlands is red sand in the flats and banded terracotta on the plateaus. Dropped on a
     * plateau it would bring its own slab of sand with it, which reads as a house teleported in
     * rather than built there — and no choice of block in the template fixes that, because any
     * one of them is wrong half the time.</p>
     *
     * <p>So the building names the block it used, and the placement swaps each one for the
     * surface its own column actually had. On red sand nothing changes; on a plateau it becomes
     * the terracotta of that exact band, because the value is the block that was there and not
     * a guess at it. The pagoda and the pyramid name nothing: grass under a pagoda in a cherry
     * grove, and sand under a compound in a desert, are never wrong.</p>
     */
    default net.minecraft.block.Block blend() {
        return null;
    }

    /** Half-width of the building's own footprint, in blocks. */
    int half();

    /**
     * Greatest height difference the building tolerates across its footprint.
     *
     * <p>Judged on {@link #half()}, never on the maze's footprint. Demanding flat ground across
     * the hundred and one blocks of a medium maze is what left the cherry style on 0.022 % of
     * the world; the maze is going underground so that the terrain only has to carry a
     * twenty-six block building.</p>
     */
    int maxRelief();

    /** Rock left between the maze's roof and the lowest ground over its footprint. */
    int clearance();

    /**
     * Lowest floor this landmark will ever put a maze on, whatever the terrain.
     *
     * <p>A guard, not a rule. {@link #maxStair} binds long before this can: a building stands on
     * the surface, so its floor is well above zero, and the stair cap already stops the maze
     * {@code maxStair} blocks below it. This exists so that a datapack with an unusual terrain
     * shape, or a dimension nobody thought about, cannot put a maze in the bedrock.</p>
     */
    int floorLimit();

    /**
     * Longest descent worth walking down, in blocks below the building's own floor.
     *
     * <p>This is the rule that keeps a site honest, and it is the answer to "no point making the
     * maze too deep". Without it the depth is whatever the terrain says, and the terrain can say
     * something absurd: a pagoda on an amplified peak at Y 200 with a valley floor at Y 50
     * inside the maze's footprint has to go down a hundred and sixty blocks — three hundred and
     * twenty slab steps, forty full turns of spiral. Capping it turns that into "there is no
     * site here", which is the right answer, because a pagoda overlooking a gorge is a bad place
     * to put one.</p>
     *
     * <p>Note what this cap <em>is</em>, stated as a rule about the ground: the building may
     * stand at most {@code maxStair - 1 - clearance - ROOF_DY} blocks above the lowest ground
     * anywhere over the maze's footprint. For the pagoda that is thirty blocks. It is still a
     * relief rule, but a <b>one-sided</b> one — only how far the ground falls counts, a hill
     * inside the footprint refuses nothing — where the surface rule it replaces allowed twelve
     * blocks in either direction.</p>
     */
    int maxStair();

    /**
     * How deep the maze goes under a landmark whose own floor is at {@code padFloorY}, or empty
     * when nothing fits.
     *
     * <p>Three things decide it, and the first is the one that is easy to get wrong: the roof has
     * to pass under the <b>lowest</b> ground anywhere over the maze's footprint, not under the
     * ground at the middle. A medium maze is 101 by 101; a pagoda on a knoll with a river forty
     * blocks away, buried just deep enough to clear the knoll, surfaces its ceiling in the river
     * bed. So the ceiling is set by {@code lowest}, measured over the whole footprint.</p>
     *
     * <p>The second is rock: of the floors that clear the ground, the <b>shallowest</b> one that
     * is actually buried in stone wins — not the best-buried one, which is what Kronos wants.
     * Here the maze is reached by a staircase somebody walks down, and every block of depth is
     * four more slab steps, so depth is a cost rather than a virtue.</p>
     *
     * <p>The third is {@link #maxStair}, which refuses the site outright when even the
     * shallowest floor that clears the ground is further down than that.</p>
     *
     * <p>There was a fourth, {@code maxDescent} — how far below the ceiling limit to keep
     * looking for rock — and measuring it in game showed it had never once had a say. It is not
     * that it happened not to fire on four sites: it <b>cannot</b> fire. The maze's footprint
     * contains the building's, so {@code lowest <= padFloorY + 1} always, and the two bounds
     * then sit {@code 1 + clearance + ROOF_DY + maxDescent - maxStair} apart in the stair cap's
     * favour — seventeen blocks of margin at the pagoda's numbers. A knob that cannot move is
     * worse than no knob, because it reads like a safeguard. The stair cap says the same thing
     * in terms of the cost that is actually paid, which is the walk down.</p>
     */
    default OptionalInt floorY(ChunkGenerator gen, HeightLimitView world, NoiseConfig noise,
                               int centerX, int centerZ, MazeSize size, int padFloorY) {
        // Measured, not judged: thresholds nothing can exceed, so the survey walks the whole
        // footprint and `lowest` is exact rather than wherever an early return stopped.
        MazeStructure.Site over = MazeStructure.surveyFloor(gen, world, noise, centerX, centerZ,
                fr.mazecraft.structure.MazePiece.spanOf(style(), size) / 2, 0,
                MazeStructure.NO_LIMIT, MazeStructure.NO_LIMIT);
        return floorY(gen, world, noise, centerX, centerZ, size, padFloorY, over);
    }

    /** The same, when the caller has already surveyed the maze's footprint. */
    default OptionalInt floorY(ChunkGenerator gen, HeightLimitView world, NoiseConfig noise,
                               int centerX, int centerZ, MazeSize size, int padFloorY,
                               MazeStructure.Site over) {
        int maxFloor = ceilingUnder(over) - fr.mazecraft.structure.MazePiece.ROOF_DY;
        // Everything here is about the TOP floor, which is the one this returns. The stair cap
        // measures from the building down to it, so that bound is unchanged. floorLimit is
        // about the deepest block the structure owns, and with a stack that is the bottom
        // floor -- so the limit has to be raised by the whole drop, or a three-floor maze digs
        // two pitches further than the limit was ever meant to allow.
        int drop = fr.mazecraft.structure.MazePiece.stackDrop(style(), size);
        int minFloor = Math.max(floorLimit() + drop, deepestForStair(padFloorY));
        return MazeStructure.findShallowestBuriedFloorY(gen, world, noise, centerX, centerZ,
                style(), size, minFloor, maxFloor, drop);
    }

    /**
     * The highest block the maze's ceiling may occupy: {@link #clearance} below the solid top of
     * the lowest ground over the footprint.
     *
     * <p>{@code getHeight} returns the first free block, so the solid top is one below it.</p>
     *
     * <p>It reads {@code lowestGround} — the sea bed — and it used to read {@code lowest}, which
     * is {@code WORLD_SURFACE_WG} and therefore the top of the <b>water</b> wherever there is
     * water. The javadoc above has always said "ground"; the code did not, and over a lake, a
     * river or a sea anywhere in a footprint a hundred and one blocks wide the two are not the
     * same number at all.</p>
     *
     * <p>What that did is visible in a {@code /maze debug depth} run taken on an ocean shore:
     * <b>"maze footprint 149x149: ground 63 to 120"</b>. Sixty-three is not a sea bed, it is
     * sea level — the heightmap was reporting the surface of the water as the ground. The
     * ceiling was then placed four blocks under <em>that</em>, so on a sea bed at 50 the roof
     * came out around 58: <b>eight blocks above the floor of the ocean</b>, a flat rectangular
     * plateau standing proud of the sand. Which is the photograph.</p>
     *
     * <p>So the underwater case was never the carvers. It is this line, and the cost of fixing
     * it is that a maze near water now has to sit deeper, which the stair cap will refuse at
     * some sites — the right refusal, since those are the sites where it was surfacing.</p>
     */
    default int ceilingUnder(MazeStructure.Site over) {
        return over.lowestGround() - 1 - clearance();
    }

    /**
     * World height of the template's own level 0, for a building standing on ground whose top
     * solid block is {@code padFloorY}.
     *
     * <p>Every other height here is derived from this one rather than assumed, because how a
     * building meets the ground is the building's business and is off by a block or two in each
     * case. The pagoda's visible ground — its grass, its paths, its gravel — is at template
     * level 1, not 0, and its floor is a step up again at level 3. Guessing it would leave either
     * a lip all round the template or a sunken courtyard.</p>
     */
    int originY(int padFloorY);

    /** Template level the staircase starts at. */
    default int stairTopTemplateY() {
        return descent().groundY();
    }

    /** World height of the top of the staircase. */
    default int stairTopY(int padFloorY) {
        return originY(padFloorY) + stairTopTemplateY();
    }

    /**
     * How far the staircase descends, in blocks, from the top of the stairs to the maze.
     *
     * <p>The lowest step sits one level above the maze floor: a bottom slab there stands half a
     * block up, so arriving on the floor is a half-block step down, which is walked without a
     * stumble — rather than ending the stair flush, which would mean replacing a block of that
     * floor with a slab. Each block of descent is two slab steps and a quarter turn, so four
     * blocks is one full turn.</p>
     */
    default int descent(int padFloorY, int mazeFloorY) {
        return stairTopY(padFloorY) - (mazeFloorY + 1);
    }

    /** The deepest floor {@link #maxStair} allows under a building whose ground is {@code padFloorY}. */
    default int deepestForStair(int padFloorY) {
        return stairTopY(padFloorY) - 1 - maxStair();
    }

    /**
     * Deepest water tolerated anywhere on the building's footprint.
     *
     * <p>It was zero, on the reasoning that a pagoda cannot stand in a pond. Measured in game,
     * that threw away the two best sites of seven: relief <b>4</b> and relief <b>5</b> — far
     * flatter than anything else surveyed — refused for water <b>2</b> and water <b>1</b>. A
     * puddle one or two blocks deep somewhere in a twenty-six block square, which the levelling
     * pass fills before a single block of the building is laid. Zero was not a rule about ponds,
     * it was a rule against stream banks, which is where a Japanese garden goes.</p>
     */
    default int maxWater() {
        return 3;
    }

    /** Is there ground here to stand the building on? Measured on the building's own footprint. */
    default MazeStructure.Site site(ChunkGenerator gen, HeightLimitView world, NoiseConfig noise,
                                    int centerX, int centerZ) {
        return MazeStructure.surveyFloor(gen, world, noise, centerX, centerZ, half(), 0,
                maxRelief(), maxWater());
    }
}
