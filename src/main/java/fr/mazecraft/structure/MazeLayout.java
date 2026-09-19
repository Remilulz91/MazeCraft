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
    private int entranceSide;
    private int entranceCell;

    private MazeLayout(int cells) {
        this.cells = cells;
        this.span = cells * CELL + 1;
        this.wall = new boolean[span * span];
        int c = cells / 2;
        this.plazaMin = CELL * (c - 1) + 1;
        this.plazaMax = CELL * (c + 1) + 3;
    }

    private boolean isPlazaCell(int cx, int cz) {
        int c = cells / 2;
        return Math.abs(cx - c) <= 1 && Math.abs(cz - c) <= 1;
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
        MazeLayout layout = new MazeLayout(cells);
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
        int doorCell = c - 1 + rng.nextInt(3);          // one of the 3 cells along that side
        int nearWall = CELL * (c - 1);                  // plaza wall line closest to x/z = 0
        int farWall = CELL * (c + 2);                   // plaza wall line closest to x/z = max
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
}
