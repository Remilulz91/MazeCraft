package fr.mazecraft.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure 2D maze layout (no Minecraft world access), fully determined by
 * {@code (cells, seed)} so every chunk regenerates exactly the same maze.
 *
 * Grid of {@code span × span} blocks where lines {@code x % 4 == 0} / {@code z % 4 == 0}
 * are walls and the 3×3 blocks in between are cell interiors. Walls between cells are
 * carved with a "growing tree" algorithm over every cell EXCEPT the 3×3 center
 * cells, which form the plaza (chest room). Then:
 * <ul>
 *   <li>one entrance is opened on a random side of the outer wall;</li>
 *   <li>the plaza gets exactly one door, on the side facing away from the entrance.</li>
 * </ul>
 * The result is a tree: there is exactly ONE path from the entrance to the center,
 * every other branch is a dead end.
 */
public final class MazeLayout {

    /** Distance between two wall lines (1 wall + 3 corridor blocks). */
    public static final int CELL = 4;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Growing-tree tuning: chance to extend the NEWEST open cell (long winding corridors,
     * like a recursive backtracker) versus a RANDOM open cell (many short side branches).
     * 1.0 = pure backtracker (few, long dead ends); lower = more junctions and dead ends.
     */
    private static final double NEWEST_CELL_CHANCE = 0.85;

    private final int cells;
    private final int span;
    private final boolean[] wall;
    private final int plazaMin;
    private final int plazaMax;
    /** Half-width of the central room, in cells: 1 is a 3×3 room, 2 a 5×5 one. */
    private final int plazaRadius;
    private int entranceSide;
    private int entranceCell;

    private MazeLayout(int cells, int plazaRadius) {
        this.cells = cells;
        this.span = cells * CELL + 1;
        this.wall = new boolean[span * span];
        this.plazaRadius = plazaRadius;
        int c = cells / 2;
        this.plazaMin = CELL * (c - plazaRadius) + 1;
        this.plazaMax = CELL * (c + plazaRadius) + 3;
    }

    private boolean isPlazaCell(int cx, int cz) {
        int c = cells / 2;
        return Math.abs(cx - c) <= plazaRadius && Math.abs(cz - c) <= plazaRadius;
    }

    /** Entrance sides. */
    public static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;

    /**
     * @param entranceSide {@link #NORTH}/{@link #SOUTH}/{@link #WEST}/{@link #EAST},
     *                     or -1 to pick it randomly from the seed.
     */
    public static MazeLayout generate(int cells, long seed, int entranceSide) {
        return generate(cells, seed, entranceSide, 0);
    }

    /**
     * @param gateCount number of gates on the path to the center (the last one is the plaza door);
     *                  0 = no gates / levers.
     */
    public static MazeLayout generate(int cells, long seed, int entranceSide, int gateCount) {
        return generate(cells, seed, entranceSide, gateCount, 1);
    }

    /**
     * @param plazaRadius half-width of the central room in cells. 1 gives the ordinary 11 × 11
     *                    chest room; 2 gives the 19 × 19 arena of Kronos, which a boss that
     *                    charges in a straight line needs in order to have any run-up at all.
     */
    public static MazeLayout generate(int cells, long seed, int entranceSide, int gateCount,
                                      int plazaRadius) {
        MazeLayout layout = new MazeLayout(cells, plazaRadius);
        layout.build(new Random(seed), entranceSide);
        if (gateCount > 0) {
            layout.computeProgression(gateCount);
        }
        return layout;
    }

    private void build(Random rng, int forcedSide) {
        // 1. Full grid of walls
        for (int x = 0; x < span; x++) {
            for (int z = 0; z < span; z++) {
                wall[x * span + z] = (x % CELL == 0) || (z % CELL == 0);
            }
        }

        // 2. Growing tree. Plaza cells are excluded from the maze: they are joined to it
        //    by a single door (step 5).
        boolean[] visited = new boolean[cells * cells];
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                if (isPlazaCell(cx, cz)) visited[cx * cells + cz] = true;
            }
        }
        List<int[]> active = new ArrayList<>();
        active.add(new int[]{0, 0});
        visited[0] = true;
        int[][] candidates = new int[4][];
        while (!active.isEmpty()) {
            int index = rng.nextDouble() < NEWEST_CELL_CHANCE ? active.size() - 1 : rng.nextInt(active.size());
            int[] cur = active.get(index);
            int n = 0;
            for (int[] d : DIRS) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < cells && nz < cells && !visited[nx * cells + nz]) {
                    candidates[n++] = new int[]{nx, nz};
                }
            }
            if (n == 0) {
                active.remove(index);
                continue;
            }
            int[] next = candidates[rng.nextInt(n)];
            carveBetween(cur[0], cur[1], next[0], next[1]);
            visited[next[0] * cells + next[1]] = true;
            active.add(next);
        }

        // 3. Central plaza: open the 3×3 center cells completely
        for (int x = plazaMin; x <= plazaMax; x++) {
            for (int z = plazaMin; z <= plazaMax; z++) {
                wall[x * span + z] = false;
            }
        }

        // 4. Entrance on a random side of the outer wall
        int randomSide = rng.nextInt(4); // always drawn so the rest of the sequence stays stable
        int side = (forcedSide >= 0 && forcedSide < 4) ? forcedSide : randomSide;
        int cell = rng.nextInt(cells);
        this.entranceSide = side;
        this.entranceCell = cell;
        for (int i = 1; i < CELL; i++) {
            int along = cell * CELL + i;
            switch (side) {
                case 0 -> wall[along * span] = false;                    // north (z = 0)
                case 1 -> wall[along * span + (span - 1)] = false;       // south (z = max)
                case 2 -> wall[along] = false;                           // west  (x = 0)
                default -> wall[(span - 1) * span + along] = false;      // east  (x = max)
            }
        }

        // 5. Single plaza door, on the plaza side facing away from the entrance
        int c = cells / 2;
        // Scaled by the plaza's half-width, which is 2 for the arena of Kronos. With these
        // written as 1 and 2 the door of a 5 × 5 room was cut in a wall line that no longer
        // existed — the room stayed sealed, and with it the whole progression.
        // At radius 1 the three expressions are what they were, draw for draw, so the sixteen
        // ordinary styles generate exactly as before.
        int doorCell = c - plazaRadius + rng.nextInt(2 * plazaRadius + 1);
        int nearWall = CELL * (c - plazaRadius);        // plaza wall line closest to x/z = 0
        int farWall = CELL * (c + plazaRadius + 1);     // plaza wall line closest to x/z = max
        for (int i = 1; i < CELL; i++) {
            int along = doorCell * CELL + i;
            switch (side) {
                case 0 -> wall[along * span + farWall] = false;   // entrance north → door south
                case 1 -> wall[along * span + nearWall] = false;  // entrance south → door north
                case 2 -> wall[farWall * span + along] = false;   // entrance west  → door east
                default -> wall[nearWall * span + along] = false; // entrance east  → door west
            }
        }
    }

    private void carveBetween(int cx, int cz, int nx, int nz) {
        if (nx != cx) {
            int wx = CELL * Math.max(cx, nx);
            for (int i = 1; i < CELL; i++) wall[wx * span + (CELL * cz + i)] = false;
        } else {
            int wz = CELL * Math.max(cz, nz);
            for (int i = 1; i < CELL; i++) wall[(CELL * cx + i) * span + wz] = false;
        }
    }

    public int span() {
        return span;
    }

    /** True if local block (lx, lz) is a wall. Out-of-range coordinates are not walls. */
    public boolean isWall(int lx, int lz) {
        if (lx < 0 || lz < 0 || lx >= span || lz >= span) return false;
        return wall[lx * span + lz];
    }

    /** True if local block (lx, lz) is a wall intersection (pillar). */
    public boolean isPillar(int lx, int lz) {
        return isWall(lx, lz) && lx % CELL == 0 && lz % CELL == 0;
    }

    /** True if local block (lx, lz) is inside the central plaza. */
    public boolean isPlaza(int lx, int lz) {
        return lx >= plazaMin && lx <= plazaMax && lz >= plazaMin && lz <= plazaMax;
    }

    public int entranceSide() {
        return entranceSide;
    }

    /**
     * Local (x, z) of a point {@code distance} blocks OUTSIDE the entrance, in the middle of
     * the opening. distance = 0 is the opening itself.
     */
    public int[] entranceOutside(int distance) {
        int along = entranceCell * CELL + CELL / 2;
        return switch (entranceSide) {
            case NORTH -> new int[]{along, -distance};
            case SOUTH -> new int[]{along, span - 1 + distance};
            case WEST -> new int[]{-distance, along};
            default -> new int[]{span - 1 + distance, along};
        };
    }

    /** True if local (lx, lz), possibly outside the maze, is on the 3-wide approach path in front of the entrance. */
    /**
     * The three (lx, lz) cells of the entrance doorway in the outer wall — the opening carved
     * in step 4 of {@link #build}. The sealed gateway of a locked maze fills exactly these.
     */
    public int[][] entranceGap() {
        int[][] cells = new int[CELL - 1][];
        for (int i = 1; i < CELL; i++) {
            int along = entranceCell * CELL + i;
            cells[i - 1] = switch (entranceSide) {
                case NORTH -> new int[]{along, 0};
                case SOUTH -> new int[]{along, span - 1};
                case WEST -> new int[]{0, along};
                default -> new int[]{span - 1, along};
            };
        }
        return cells;
    }

    /** True when the entrance doorway runs along the X axis (north and south walls). */
    public boolean entranceAlongX() {
        return entranceSide == NORTH || entranceSide == SOUTH;
    }

    public boolean isApproach(int lx, int lz) {
        int along = entranceCell * CELL + CELL / 2;
        return switch (entranceSide) {
            case NORTH -> lz < 0 && Math.abs(lx - along) <= 1;
            case SOUTH -> lz >= span && Math.abs(lx - along) <= 1;
            case WEST -> lx < 0 && Math.abs(lz - along) <= 1;
            default -> lx >= span && Math.abs(lz - along) <= 1;
        };
    }

    /** True if local (lx, lz) is inside the maze footprint (walls + corridors, not the margin around it). */
    public boolean isInside(int lx, int lz) {
        return lx >= 0 && lz >= 0 && lx < span && lz < span;
    }

    /** Local coordinate of the maze center (same on both axes). */
    public int center() {
        return span / 2;
    }

    // =====================================================================
    // Progression: gates on the solution path + one lever per gate
    // =====================================================================

    /** A gate: 3 blocks filling a wall opening (local coords, inclusive range). */
    public record Gate(int x0, int z0, int x1, int z1, boolean alongX) {
        /** alongX = the 3 blocks are lined up along X (wall line at constant Z). */
    }

    /**
     * A lever on a wall. (x, z) = lever block, (supportX, supportZ) = wall block it hangs on,
     * facing = direction the lever faces (NORTH/SOUTH/WEST/EAST constants of this class).
     */
    public record Lever(int x, int z, int supportX, int supportZ, int facing) { }

    private static final int[][] CELL_DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // N, S, W, E (same order as sides)

    private final List<Gate> gates = new ArrayList<>();
    private final List<Lever> levers = new ArrayList<>();
    private int[] component; // component index per cell (-1 = plaza)
    private int[] parentCell;  // cell tree, built on demand for detour lengths
    private int[] cellDepth;

    /** Floor on how far apart, in cells, the two sides of a shifting wall must be. */
    public static final int MIN_DETOUR_CELLS = 12;

    /**
     * How far apart the two sides of a shifting wall must be for it to be worth opening, in
     * cells — one cell is four blocks. Scaled with the maze, since what counts as a long way
     * round depends on how big the maze is, but never below {@link #MIN_DETOUR_CELLS}: under
     * that the wall opens onto a corridor the player can already see.
     */
    public int minDetour() {
        return Math.max(MIN_DETOUR_CELLS, cells / 3);
    }

    public List<Gate> gates() {
        return Collections.unmodifiableList(gates);
    }

    public List<Lever> levers() {
        return Collections.unmodifiableList(levers);
    }

    /** Component (zone) of a cell, 0 = entrance zone; -1 for plaza cells. Only valid after progression. */
    public int componentOf(int cx, int cz) {
        return component == null ? 0 : component[cx * cells + cz];
    }

    private boolean isOpenBetween(int cx, int cz, int dir) {
        int nx = cx + CELL_DIRS[dir][0], nz = cz + CELL_DIRS[dir][1];
        if (nx < 0 || nz < 0 || nx >= cells || nz >= cells) return false;
        int[] mid = wallMiddle(cx, cz, dir);
        return !wall[mid[0] * span + mid[1]];
    }

    /**
     * The one way out of a dead-end cell, as a {@link #NORTH}..{@link #EAST} side.
     *
     * <p>Used to lay the vault's spiral stair the right way round: the top step has to be the
     * one under the doorway, or the player walks into the dead end and finds the first step on
     * the far side of a hole.</p>
     *
     * @return the side, or -1 when the cell is not a dead end
     */
    public int onlyExit(int cx, int cz) {
        int found = -1;
        for (int d = 0; d < 4; d++) {
            if (!isOpenBetween(cx, cz, d)) continue;
            if (found >= 0) return -1; // more than one way out: not a dead end
            found = d;
        }
        return found;
    }

    /** Local block in the middle of the wall on side {@code dir} of a cell. */
    public static int[] wallMiddleOf(int cx, int cz, int dir) {
        return wallMiddle(cx, cz, dir);
    }

    /** Local block in the middle of the wall on side {@code dir} of a cell. */
    private static int[] wallMiddle(int cx, int cz, int dir) {
        return switch (dir) {
            case NORTH -> new int[]{CELL * cx + 2, CELL * cz};
            case SOUTH -> new int[]{CELL * cx + 2, CELL * cz + CELL};
            case WEST -> new int[]{CELL * cx, CELL * cz + 2};
            default -> new int[]{CELL * cx + CELL, CELL * cz + 2};
        };
    }

    private Gate gateBetween(int cx, int cz, int dir) {
        return switch (dir) {
            case NORTH -> new Gate(CELL * cx + 1, CELL * cz, CELL * cx + 3, CELL * cz, true);
            case SOUTH -> new Gate(CELL * cx + 1, CELL * cz + CELL, CELL * cx + 3, CELL * cz + CELL, true);
            case WEST -> new Gate(CELL * cx, CELL * cz + 1, CELL * cx, CELL * cz + 3, false);
            default -> new Gate(CELL * cx + CELL, CELL * cz + 1, CELL * cx + CELL, CELL * cz + 3, false);
        };
    }

    private int entranceCellIndex() {
        return switch (entranceSide) {
            case NORTH -> entranceCell * cells;                    // (entranceCell, 0)
            case SOUTH -> entranceCell * cells + (cells - 1);      // (entranceCell, cells-1)
            case WEST -> entranceCell;                             // (0, entranceCell)
            default -> (cells - 1) * cells + entranceCell;         // (cells-1, entranceCell)
        };
    }

    /**
     * Cuts the unique entrance → center path with {@code gateCount} gates (the last one is the
     * plaza door), which splits the maze into zones. Each zone gets the lever of the gate that
     * leaves it, placed at the dead end farthest from where the player enters that zone.
     */
    private void computeProgression(int gateCount) {
        int n = cells * cells;
        int start = entranceCellIndex();

        // 1. BFS from the entrance over maze cells (plaza excluded)
        int[] parent = new int[n];
        Arrays.fill(parent, -2);
        parent[start] = -1;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int doorCell = -1, doorDir = -1;
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int cx = cur / cells, cz = cur % cells;
            for (int d = 0; d < 4; d++) {
                if (!isOpenBetween(cx, cz, d)) continue;
                int nx = cx + CELL_DIRS[d][0], nz = cz + CELL_DIRS[d][1];
                if (isPlazaCell(nx, nz)) {
                    doorCell = cur;
                    doorDir = d;
                    continue;
                }
                int ni = nx * cells + nz;
                if (parent[ni] != -2) continue;
                parent[ni] = cur;
                queue.add(ni);
            }
        }
        if (doorCell < 0) return; // cannot happen with a valid layout

        // 2. Solution path entrance → door cell
        List<Integer> path = new ArrayList<>();
        for (int c = doorCell; c != -1; c = parent[c]) path.add(c);
        Collections.reverse(path);
        int len = path.size();

        // 3. Gate edges: evenly spaced along the path, then the plaza door
        //    blocked[cell*4+dir] marks an edge closed by a gate
        boolean[] blocked = new boolean[n * 4];
        List<Integer> zoneStarts = new ArrayList<>();
        zoneStarts.add(start);
        int lastIndex = -1;
        for (int k = 1; k < gateCount; k++) {
            int i = (int) ((long) k * (len - 1) / gateCount); // edge path[i] → path[i+1]
            if (i <= lastIndex || i >= len - 1) continue;      // path too short for this many gates
            lastIndex = i;
            int a = path.get(i), b = path.get(i + 1);
            int dir = dirBetween(a, b);
            blocked[a * 4 + dir] = true;
            blocked[b * 4 + (dir ^ 1)] = true;
            gates.add(gateBetween(a / cells, a % cells, dir));
            zoneStarts.add(b);
        }
        gates.add(gateBetween(doorCell / cells, doorCell % cells, doorDir)); // plaza door

        // 4. Zones = connected components once gate edges are removed
        component = new int[n];
        Arrays.fill(component, -1);
        for (int z = 0; z < zoneStarts.size(); z++) {
            int[] dist = new int[n];
            Arrays.fill(dist, -1);
            int s0 = zoneStarts.get(z);
            dist[s0] = 0;
            component[s0] = z;
            queue.add(s0);
            int best = -1, bestDist = -1, far = s0, farDist = 0;
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                int cx = cur / cells, cz = cur % cells;
                int degree = 0;
                for (int d = 0; d < 4; d++) {
                    if (!isOpenBetween(cx, cz, d)) continue;
                    int nx = cx + CELL_DIRS[d][0], nz = cz + CELL_DIRS[d][1];
                    degree++;
                    if (blocked[cur * 4 + d] || isPlazaCell(nx, nz)) continue;
                    int ni = nx * cells + nz;
                    if (dist[ni] >= 0) continue;
                    dist[ni] = dist[cur] + 1;
                    component[ni] = z;
                    queue.add(ni);
                }
                if (dist[cur] > farDist) {
                    far = cur;
                    farDist = dist[cur];
                }
                boolean deadEnd = degree == 1 && cur != start && !touchesGate(cur, blocked);
                if (deadEnd && dist[cur] > bestDist) {
                    best = cur;
                    bestDist = dist[cur];
                }
            }
            levers.add(leverIn(best >= 0 ? best : far));
        }
    }

    private boolean touchesGate(int cell, boolean[] blocked) {
        for (int d = 0; d < 4; d++) if (blocked[cell * 4 + d]) return true;
        return false;
    }

    private int dirBetween(int a, int b) {
        int dx = b / cells - a / cells, dz = b % cells - a % cells;
        for (int d = 0; d < 4; d++) {
            if (CELL_DIRS[d][0] == dx && CELL_DIRS[d][1] == dz) return d;
        }
        throw new IllegalStateException("cells are not adjacent");
    }

    /** Lever on a closed wall of the cell, preferring the wall facing its only opening. */
    private Lever leverIn(int cell) {
        int cx = cell / cells, cz = cell % cells;
        int open = -1;
        for (int d = 0; d < 4; d++) if (isOpenBetween(cx, cz, d)) { open = d; break; }
        int side = -1;
        if (open >= 0 && isClosedSide(cx, cz, open ^ 1)) side = open ^ 1;
        for (int d = 0; side < 0 && d < 4; d++) if (isClosedSide(cx, cz, d)) side = d;
        if (side < 0) side = NORTH;

        int[] support = wallMiddle(cx, cz, side);
        int lx = support[0] - CELL_DIRS[side][0];
        int lz = support[1] - CELL_DIRS[side][1];
        return new Lever(lx, lz, support[0], support[1], side ^ 1); // faces away from the wall
    }

    private boolean isClosedSide(int cx, int cz, int dir) {
        int[] mid = wallMiddle(cx, cz, dir);
        return mid[0] >= 0 && mid[1] >= 0 && mid[0] < span && mid[1] < span && wall[mid[0] * span + mid[1]];
    }

    // =====================================================================
    // Guardians
    // =====================================================================

    /**
     * Picks guardian cells: {@code min..max} per zone, dead ends first (where levers and
     * treasure-hunters go), never the entrance cell, the plaza or a lever cell.
     *
     * @return list of {cellX, cellZ, zone}
     */
    public List<int[]> guardianCells(Random rng, int min, int max) {
        List<int[]> result = new ArrayList<>();
        if (component == null) return result;
        int zones = levers.size();
        int entrance = entranceCellIndex();
        boolean[] leverCell = new boolean[cells * cells];
        for (Lever l : levers) leverCell[(l.x() / CELL) * cells + (l.z() / CELL)] = true;

        for (int zone = 0; zone < zones; zone++) {
            List<Integer> deadEnds = new ArrayList<>();
            List<Integer> others = new ArrayList<>();
            for (int c = 0; c < cells * cells; c++) {
                if (component[c] != zone || c == entrance || leverCell[c]) continue;
                int cx = c / cells, cz = c % cells;
                int degree = 0;
                for (int d = 0; d < 4; d++) if (isOpenBetween(cx, cz, d)) degree++;
                (degree == 1 ? deadEnds : others).add(c);
            }
            Collections.shuffle(deadEnds, rng);
            Collections.shuffle(others, rng);
            deadEnds.addAll(others);
            int count = Math.min(deadEnds.size(), min + rng.nextInt(max - min + 1));
            for (int k = 0; k < count; k++) {
                int c = deadEnds.get(k);
                result.add(new int[]{c / cells, c % cells, zone});
            }
        }
        return result;
    }

    /**
     * Wall segments that may open and close while the maze is being walked — the shifting walls
     * of Kronos.
     *
     * <p>The maze is a tree: exactly one path between any two cells. Opening a wall therefore
     * adds a loop and can never cut anything off, and closing it again restores the original
     * tree. That is the whole safety argument for the moving walls — no connectivity check is
     * needed at any point, and a player can never be sealed away from the centre.</p>
     *
     * <p>The one thing that must not happen is a shortcut <em>past a gate</em>. A loop crossing
     * a zone boundary would let a player reach the next zone without pulling its lever, which is
     * the entire progression. So a segment only qualifies when the cells on both sides belong to
     * the same zone; the loop then stays inside it. Plaza cells, gates and the walls holding
     * levers are excluded outright.</p>
     *
     * <p>A segment also has to be <em>worth</em> opening. Picked at random, most of them join
     * two corridors that are already a few steps apart, and a wall that grinds open onto nothing
     * is just noise. Each candidate is therefore scored by how far apart its two sides are along
     * the maze — the detour it saves — and only the ones past {@link #minDetour()} are kept.
     * Because the maze is a tree, that distance is the unique path between them, and since both
     * cells share a zone the path never leaves it. Measured over 960 generated mazes, a plain
     * random draw put a quarter of the shifting walls between corridors already three cells
     * apart, and over half of them under twelve; scored this way, a colossal maze's forty
     * segments save 19 cells at the very least, 41 at the median — a corridor that was over a
     * hundred and fifty blocks of walking away is suddenly one step through the wall.</p>
     *
     * @return at most {@code max} segments, chosen deterministically from {@code seed}
     */
    public List<Gate> movableWalls(long seed, int max) {
        List<Gate> found = new ArrayList<>();
        List<Integer> detours = new ArrayList<>();
        if (component == null) return found;

        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                // Only SOUTH and EAST, so every wall between two cells is considered once.
                for (int dir : new int[]{SOUTH, EAST}) {
                    int nx = cx + CELL_DIRS[dir][0], nz = cz + CELL_DIRS[dir][1];
                    if (nx < 0 || nz < 0 || nx >= cells || nz >= cells) continue;
                    if (isOpenBetween(cx, cz, dir)) continue;          // already a passage

                    int here = componentOf(cx, cz), there = componentOf(nx, nz);
                    if (here < 0 || there < 0 || here != there) continue; // plaza, or across a gate

                    Gate candidate = gateBetween(cx, cz, dir);
                    if (overlapsGate(candidate) || holdsLever(candidate)) continue;
                    found.add(candidate);
                    detours.add(treeDistance(cx, cz, nx, nz));
                }
            }
        }

        // Keep the ones that actually save a walk. If too few qualify, fall back to the longest
        // detours available rather than padding the list with pointless ones.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        int bar = minDetour();
        List<Integer> worthwhile = new ArrayList<>();
        for (int i : order) if (detours.get(i) >= bar) worthwhile.add(i);

        Random rng = new Random(seed);
        List<Integer> chosen;
        if (worthwhile.size() >= max) {
            Collections.shuffle(worthwhile, rng);
            chosen = worthwhile.subList(0, max);
        } else {
            order.sort((a, b) -> Integer.compare(detours.get(b), detours.get(a)));
            chosen = order.subList(0, Math.min(max, order.size()));
        }

        List<Gate> result = new ArrayList<>();
        for (int i : chosen) result.add(found.get(i));
        return result;
    }

    /** Shortest detour, in cells, a segment saves: the maze is a tree, so this is the only path. */
    private int treeDistance(int ax, int az, int bx, int bz) {
        if (parentCell == null) buildCellTree();
        int a = ax * cells + az, b = bx * cells + bz;
        // Every cell is carved, so both are in the tree. Guarded all the same: walking up from a
        // cell the flood never reached would never meet the other side, and the loop below would
        // not end. Such a segment is simply not a candidate.
        if (cellDepth[a] < 0 || cellDepth[b] < 0) return -1;
        int steps = 0;
        while (a != b) {
            if (cellDepth[a] < cellDepth[b]) { b = parentCell[b]; } else { a = parentCell[a]; }
            steps++;
        }
        return steps;
    }

    private void buildCellTree() {
        int n = cells * cells;
        parentCell = new int[n];
        cellDepth = new int[n];
        Arrays.fill(parentCell, -1);
        Arrays.fill(cellDepth, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        cellDepth[0] = 0;
        parentCell[0] = 0;
        while (!queue.isEmpty()) {
            int c = queue.poll();
            int cx = c / cells, cz = c % cells;
            for (int dir = 0; dir < 4; dir++) {
                if (!isOpenBetween(cx, cz, dir)) continue;
                int nx = cx + CELL_DIRS[dir][0], nz = cz + CELL_DIRS[dir][1];
                int ni = nx * cells + nz;
                if (cellDepth[ni] >= 0) continue;
                cellDepth[ni] = cellDepth[c] + 1;
                parentCell[ni] = c;
                queue.add(ni);
            }
        }
    }


    /**
     * The dead end that hosts the hidden vault of a colossal maze: the stair down starts here.
     *
     * <p>Chosen rather than drawn at random, against four rules that each close a way the room
     * could ruin something else. It must be a <b>true dead end</b> (one way in), so the stair
     * cannot sit in a corridor people walk through. It must be <b>{@value
     * #VAULT_LEVER_CLEARANCE} cells clear of every lever</b> — the stair must not swallow the
     * thing that opens a gate, and a lever you can see from the mouth of the stair hands the
     * vault to anybody who was only there to pull it. It must <b>not touch a gate or the
     * plaza</b>, so the room never becomes a way around the progression. And among what is
     * left, the one <b>furthest from the entrance</b> wins, because a vault found in the first
     * corridor is not hidden.</p>
     *
     * <p>Everything about the room lives below the maze floor, so none of this changes the maze
     * itself: no cell is merged, the tree is untouched, and the zones and gates are exactly
     * what they were.</p>
     *
     * @param clearance cells the chosen dead end must keep from the maze's outer wall, so the
     *                  room below fits inside the protected footprint
     * @return {@code {cx, cz}} of the chosen cell, or null when the maze offers nowhere suitable
     */
    public int[] vaultDeadEnd(int clearance) {
        int[] found = vaultDeadEnd(clearance, VAULT_LEVER_CLEARANCE);
        // A colossal maze has hundreds of dead ends and the rule has never cost one in testing,
        // but a vault somewhere imperfect beats a colossal maze with no vault at all.
        return found != null ? found : vaultDeadEnd(clearance, 1);
    }

    /**
     * How many cells the vault's dead end keeps from the nearest lever.
     *
     * <p>Four. Measured over 600 colossal mazes: excluding only the lever's own cell left a
     * lever in the next cell 1.5% of the time and within two cells 11% of the time, close
     * enough to see the mouth of the stair from. Four cells costs <b>two cells of walking
     * distance</b> — the median stays 348 cells from the entrance against 350 — and never once
     * failed to find a dead end, because there are hundreds to choose from.</p>
     */
    private static final int VAULT_LEVER_CLEARANCE = 4;

    private int[] vaultDeadEnd(int clearance, int leverClearance) {
        if (component == null) return null;

        List<int[]> leverCells = new ArrayList<>();
        for (Lever lever : levers) {
            leverCells.add(new int[]{lever.supportX() / CELL, lever.supportZ() / CELL});
            leverCells.add(new int[]{lever.x() / CELL, lever.z() / CELL});
        }

        // Distance in cells from the entrance, walking the maze.
        int[] dist = entranceDistances();
        int start = entranceCellIndex();

        int best = -1, bestDist = -1;
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                // The room is dug around this cell and must stay inside the maze's own
                // footprint: outside it, nothing protects it and the repair pass does not
                // know it exists.
                if (cx < clearance || cz < clearance
                        || cx >= cells - clearance || cz >= cells - clearance) continue;
                int cell = cx * cells + cz;
                if (dist[cell] < 0 || cell == start) continue;
                if (isPlazaCell(cx, cz) || componentOf(cx, cz) < 0) continue;
                if (nearestLeverCells(leverCells, cx, cz) < leverClearance) continue;
                int degree = 0;
                for (int d = 0; d < 4; d++) if (isOpenBetween(cx, cz, d)) degree++;
                if (degree != 1) continue;
                if (touchesAnyGate(cx, cz)) continue;
                if (dist[cell] > bestDist) {
                    best = cell;
                    bestDist = dist[cell];
                }
            }
        }
        return best < 0 ? null : new int[]{best / cells, best % cells};
    }

    /**
     * The dead ends that carry the four digits of the vault's code — one per zone.
     *
     * <p><b>One per zone, and that is the whole design.</b> A colossal maze has several hundred
     * dead ends; four plaques hidden anywhere among them would be a needle-in-a-haystack search
     * nobody finishes. But the maze is already cut into zones by its gates, and a player sweeps
     * each zone looking for its lever anyway. Putting one digit in each of the first four zones
     * means the code is collected along the way the player was already going, in the order the
     * maze opens — and still never handed over, because a dead end has to be walked into.</p>
     *
     * <p>Each plaque takes the dead end <b>furthest from the entrance within its own zone</b>,
     * by the same rules the vault uses, so a digit is never the first thing in a corridor.</p>
     *
     * @param clearance cells to keep from the outer wall
     * @param slots how many digits the code has
     * @param skip a cell to leave alone (the vault's own dead end), or null
     * @return one {@code {cx, cz}} per slot; an entry is null when its zone offered nothing
     */
    public int[][] codePlaqueCells(int clearance, int slots, int[] skip) {
        if (component == null) return null;

        List<int[]> leverCells = new ArrayList<>();
        for (Lever lever : levers) {
            leverCells.add(new int[]{lever.supportX() / CELL, lever.supportZ() / CELL});
            leverCells.add(new int[]{lever.x() / CELL, lever.z() / CELL});
        }
        int[] dist = entranceDistances();
        int start = entranceCellIndex();

        int[][] found = new int[slots][];
        int[] bestDist = new int[slots];
        java.util.Arrays.fill(bestDist, -1);
        // Every dead end that passes the rules, and every dead end that fails only the lever
        // or gate rule, both kept against the possibility that a zone has nothing to offer.
        List<int[]> clean = new ArrayList<>();
        List<int[]> loose = new ArrayList<>();

        for (int cx = clearance; cx < cells - clearance; cx++) {
            for (int cz = clearance; cz < cells - clearance; cz++) {
                int cell = cx * cells + cz;
                if (dist[cell] < 0 || cell == start) continue;
                if (skip != null && cx == skip[0] && cz == skip[1]) continue;
                if (isPlazaCell(cx, cz)) continue;
                int zone = componentOf(cx, cz);
                if (zone < 0) continue;
                if (onlyExit(cx, cz) < 0) continue;
                // Two cells from a lever is enough here: unlike the vault's stair, a plaque on
                // a wall takes nothing away and costs nothing if it is seen on the way past.
                boolean ok = nearestLeverCells(leverCells, cx, cz) >= 2 && !touchesAnyGate(cx, cz);
                (ok ? clean : loose).add(new int[]{cx, cz, dist[cell]});
                if (!ok || zone >= slots) continue;
                if (dist[cell] > bestDist[zone]) {
                    bestDist[zone] = dist[cell];
                    found[zone] = new int[]{cx, cz};
                }
            }
        }

        // A zone with no dead end of its own would leave a digit of the code unwritten, and a
        // code with a digit missing is a door that never opens. Measured at 3.3% of colossal
        // mazes, which is far too often for a dead end. So a slot the zones could not fill
        // takes the furthest dead end left anywhere — out of its zone, which costs the player
        // nothing but the tidiness of one digit per zone, and failing that one that sits near
        // a lever or a gate, which is only untidy. The code stays readable either way.
        fillGaps(found, clean);
        fillGaps(found, loose);
        return found;
    }

    /** Hands the furthest unused dead end to each slot still empty. */
    private static void fillGaps(int[][] found, List<int[]> candidates) {
        boolean any = false;
        for (int[] slot : found) if (slot == null) any = true;
        if (!any || candidates.isEmpty()) return;

        candidates.sort((a, b) -> Integer.compare(b[2], a[2]));
        for (int slot = 0; slot < found.length; slot++) {
            if (found[slot] != null) continue;
            for (int[] candidate : candidates) {
                boolean taken = false;
                for (int[] used : found) {
                    if (used != null && used[0] == candidate[0] && used[1] == candidate[1]) {
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                found[slot] = new int[]{candidate[0], candidate[1]};
                break;
            }
        }
    }

    /** Cells walked from the entrance to every cell, or -1 where the cell is unreachable. */
    private int[] entranceDistances() {
        int[] dist = new int[cells * cells];
        java.util.Arrays.fill(dist, -1);
        int start = entranceCellIndex();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        dist[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int cx = cur / cells, cz = cur % cells;
            for (int d = 0; d < 4; d++) {
                if (!isOpenBetween(cx, cz, d)) continue;
                int nx = cx + CELL_DIRS[d][0], nz = cz + CELL_DIRS[d][1];
                int ni = nx * cells + nz;
                if (dist[ni] >= 0) continue;
                dist[ni] = dist[cur] + 1;
                queue.add(ni);
            }
        }
        return dist;
    }

    /** Cells (as the crow flies over the grid) from this cell to the nearest lever. */
    private static int nearestLeverCells(List<int[]> leverCells, int cx, int cz) {
        int best = Integer.MAX_VALUE;
        for (int[] cell : leverCells) {
            best = Math.min(best, Math.abs(cell[0] - cx) + Math.abs(cell[1] - cz));
        }
        return best;
    }

    /** Does any gate box touch this cell's four walls? */
    private boolean touchesAnyGate(int cx, int cz) {
        for (int d = 0; d < 4; d++) {
            int[] m = wallMiddle(cx, cz, d);
            for (Gate gate : gates) {
                if (m[0] >= gate.x0() - 1 && m[0] <= gate.x1() + 1
                        && m[1] >= gate.z0() - 1 && m[1] <= gate.z1() + 1) return true;
            }
        }
        return false;
    }

    private boolean overlapsGate(Gate candidate) {
        for (Gate gate : gates) {
            if (candidate.x0() <= gate.x1() && candidate.x1() >= gate.x0()
                    && candidate.z0() <= gate.z1() && candidate.z1() >= gate.z0()) return true;
        }
        return false;
    }

    private boolean holdsLever(Gate candidate) {
        for (Lever lever : levers) {
            if (within(candidate, lever.x(), lever.z()) || within(candidate, lever.supportX(), lever.supportZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(Gate g, int x, int z) {
        return x >= g.x0() && x <= g.x1() && z >= g.z0() && z <= g.z1();
    }

    /** Number of zones (= number of levers). */
    public int zoneCount() {
        return levers.size();
    }
}
