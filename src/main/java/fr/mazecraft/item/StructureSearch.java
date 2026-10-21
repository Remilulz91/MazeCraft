package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;

/**
 * Finding a maze from an item, without killing the server doing it.
 *
 * <p>Three items look structures up — Ariadne's Compass, Ariadne's Thread and the Key of Kronos
 * — and all three had the same bug, because all three had the same misreading of one number.
 * {@code ServerWorld.locateStructure}'s radius is <b>not</b> in chunks. It counts rings on the
 * structure set's own spread grid, so the area it sweeps is the radius times the set's spacing:
 * 40 chunks for a maze, 200 for Kronos. Vanilla's own {@code /locate} passes 100, which is where
 * the number came from, and at 40 chunks of spacing that is 40,401 candidate sites examined
 * inside a single server tick. A dedicated server's watchdog kills the whole server when one
 * tick runs 60 seconds. It did, twice.</p>
 *
 * <p>So reach is no longer bought in one call. Each use of an item runs <b>one</b> sweep over a
 * radius the tick can afford, and remembers on the stack which sweep comes next: use the item
 * again and it looks at fresh ground further out. The reach is spread over several uses instead
 * of several seconds of frozen server.</p>
 *
 * <p>Every call is timed, and a slow one says so in the log with the number in it. That line is
 * the only thing that turns the next occurrence into something actionable rather than a
 * post-mortem — the first crash arrived with nothing in front of it at all.</p>
 */
public final class StructureSearch {

    /**
     * Last sweep offered: sweep 0 on the holder, a ring of eight around them, then a ring of
     * sixteen further out.
     *
     * <p>The second ring exists for the rare styles. Measured over three scans from cherry
     * groves thousands of blocks apart — and they agreed, 1 site in 237, 238 and 249 — a cherry
     * grove covers about one position in 241 and five percent of those are flat enough, which
     * puts a cherry maze at 0.022 % of positions against 2 % for a hedge one. Nine sweeps find
     * one 71 % of the time; three players in ten found nothing, and since the sweep counter
     * then resets onto the same nine origins, clicking again searched the same ground and was
     * guaranteed to fail. Twenty-five sweeps take that to 97 %.</p>
     *
     * <p>It costs nothing. A sweep examines the same 625 candidate sites whatever ring it sits
     * on, so the server pays per click exactly what it paid before; the player simply keeps
     * clicking on a style that is genuinely rare, which is what rare ought to feel like. The
     * alternative was to loosen the terrain rule for cherry grove, which buys a comparable
     * improvement but changes the world to do it.</p>
     */
    public static final int LAST_SWEEP = 24;

    /**
     * How far out each ring sits, in units of one sweep's own reach, and how many sweeps it
     * holds.
     *
     * <p>Both distances are measured over the whole disc rather than reasoned about, because
     * reasoning got the first one wrong: the obvious value for the inner ring is 2, and at 2
     * the eight discs touch the middle one at a single point each and leave a hole on all
     * eight bisectors, starting a hair outside the middle sweep's rim. 1.75 leaves no
     * uncovered point within 2.35 sweeps.</p>
     *
     * <p>The outer ring is 3.1, which carries continuous coverage out to 3.8 sweeps. 3.2
     * reaches 3.9 and was the first candidate, but its tightest point sits 0.9984 of a sweep
     * from the nearest origin — a margin of 0.2 %, which is not a margin, it is a coincidence.
     * 3.1 holds 2.9 % clear for a tenth of a sweep less reach. Past 3.4 the outer discs
     * detach from the inner ring entirely and the thing has a hole in it again.</p>
     */
    private static final double RING_1 = 1.75;
    private static final int RING_1_COUNT = 8;
    private static final double RING_2 = 3.1;
    private static final int RING_2_COUNT = 16;

    /**
     * Above this, a search goes in the log as a warning whatever the build.
     *
     * <p>This was 250 ms, which was the right value for exactly one server test and the wrong
     * one to ship. The test gave the number the guesswork had been missing: a search costs
     * about <b>1 ms per candidate site</b>, almost all of it vanilla's own synchronous
     * chunk-NBT lookup, one per candidate, so a sweep that finds nothing and therefore walks
     * all 625 of them lands at 300–800 ms <em>by design</em>. At 250 ms the warning fired on
     * normal operation, 30 times in four minutes — and a warning that cries on healthy
     * behaviour is worse than no warning at all, because it is the line that would have to
     * stand out when something really does regress.</p>
     *
     * <p>3 s is five times a normal full sweep and twenty times under the watchdog: quiet when
     * the mod is behaving, loud before anything is actually at risk.</p>
     */
    private static final long SLOW_MS = 3_000;

    /** What a dedicated server's watchdog allows one tick, for scale in the warning. */
    private static final long WATCHDOG_MS = 60_000;

    private StructureSearch() { }

    /**
     * One timed {@code locateStructure} call. Never loop this: looping it is what the sweep
     * counter is for.
     *
     * @param radius rings of the structure set's spread grid — see the class comment
     * @param what   what is being looked for, for the log line
     */
    public static BlockPos locate(ServerWorld world, TagKey<Structure> tag, BlockPos from,
                                  int radius, String what) {
        long started = System.nanoTime();
        BlockPos found = world.locateStructure(tag, from, radius, false);
        long ms = (System.nanoTime() - started) / 1_000_000L;
        // ASCII only in anything that goes to a log. A server console on Windows runs a legacy
        // code page, where the em dash this line used to carry came out as "ÔÇö" in the middle
        // of the one sentence whose whole job is to be readable by whoever pastes it back.
        if (ms >= SLOW_MS) {
            MazeCraft.LOGGER.warn("[MazeCraft] a search for {} held the server thread {} ms "
                    + "(radius {}), well over the ~1 ms per candidate site this should cost. "
                    + "A dedicated server's watchdog kills the server at {} ms in one tick. "
                    + "Please report this line.", what, ms, radius, WATCHDOG_MS);
        } else if (MazeCraft.isDebugBuild()) {
            MazeCraft.LOGGER.info("[MazeCraft] search for {} took {} ms (radius {})", what, ms, radius);
        }
        return found;
    }

    /**
     * Where sweep number {@code sweep} is centred, for an item whose one sweep reaches
     * {@code reach} blocks.
     */
    public static BlockPos sweepOrigin(BlockPos from, int sweep, int reach) {
        if (sweep <= 0) return from;
        double angle;
        double out;
        if (sweep <= RING_1_COUNT) {
            angle = Math.PI * 2.0 * (sweep - 1) / RING_1_COUNT;
            out = reach * RING_1;
        } else {
            // Offset by half a step so the outer origins sit between the inner ones rather
            // than behind them, which is what closes the gaps on the inner ring's bisectors.
            angle = Math.PI * 2.0 * (sweep - RING_1_COUNT - 1) / RING_2_COUNT
                    + Math.PI / RING_2_COUNT;
            out = reach * RING_2;
        }
        return new BlockPos(
                from.getX() + (int) Math.round(Math.cos(angle) * out),
                from.getY(),
                from.getZ() + (int) Math.round(Math.sin(angle) * out));
    }

    /**
     * Blocks one sweep of this radius reaches, read from the spread grid the structures in this
     * tag are actually placed on.
     *
     * <p>This used to be a constant 40 chunks, written from the one structure set file I
     * happened to open. The maze sets are <b>24 for a small maze, 32 for a medium, 40 for a
     * large</b> and 200 for Kronos, so the constant was right for exactly one of the four — and
     * the sweep ring, which is placed at 1.75 reaches out, was therefore placed 13 440 blocks
     * away from discs only 4 608 blocks wide. For small mazes the nine sweeps left a blind
     * annulus more than four thousand blocks across, right where the player is standing. Two
     * styles looked unfindable and I spent a day measuring the worldgen rules; the geometry I
     * had checked was only ever valid for large mazes, which are the one size nobody tested.</p>
     *
     * <p>The number of candidate sites a sweep examines — and so its cost — depends only on the
     * radius, never on the spacing: 625 chances at radius 12, whatever the size. Only the
     * distance those chances span changes, which is exactly what the ring needs to know.</p>
     *
     * <p>This figure is a <b>lower bound</b> on what a sweep really reaches, and deliberately
     * so. {@code locateStructure} walks concentric <em>square</em> rings of grid cells, so the
     * ground it covers is a square of this half-width, not a disc of this radius — its corners
     * extend about 1.4 times further — and a structure sits anywhere inside its cell, which
     * adds up to one more cell again. The ring geometry below treats each sweep as the disc
     * inscribed in that square, so every point it calls covered genuinely is, and some it
     * writes off as out of range are reached anyway. A cherry maze found on sweep 17 sat
     * slightly beyond the nominal reach of that sweep, which is how this was pinned down.
     * Erring in this direction is the only safe one: the cost of underestimating is a few
     * redundant sweeps, the cost of overestimating is a blind ring.</p>
     */
    public static int reach(ServerWorld world, TagKey<Structure> tag, int radius) {
        return radius * spacingOf(world, tag) * 16;
    }

    /** Fallback when a tag matches no random-spread set: the widest maze grid. */
    private static final int DEFAULT_SPACING = 40;

    /**
     * The tightest spread grid among the structures of this tag, in chunks.
     *
     * <p>The tightest, not the average: a tag holding several sets is searched ring by ring in
     * each set's own grid, so the distance the whole call is <em>guaranteed</em> to have covered
     * is the one from the densest grid. Assuming more would put the next sweep past ground
     * nothing has looked at. This matters for Ariadne's Thread, whose tag holds all 48.</p>
     */
    private static int spacingOf(ServerWorld world, TagKey<Structure> tag) {
        var structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var tagged = structures.getEntryList(tag).orElse(null);
        if (tagged == null) return DEFAULT_SPACING;
        int tightest = Integer.MAX_VALUE;
        for (StructureSet set : world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET)) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) continue;
            for (StructureSet.WeightedEntry weighted : set.structures()) {
                for (RegistryEntry<Structure> entry : tagged) {
                    if (entry.value() == weighted.structure().value()) {
                        tightest = Math.min(tightest, spread.getSpacing());
                    }
                }
            }
        }
        return tightest == Integer.MAX_VALUE ? DEFAULT_SPACING : tightest;
    }

    /** The sweep stored under this key, clamped: bad data restarts the search rather than hanging. */
    public static int storedSweep(ItemStack stack, String key) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return 0;
        int sweep = data.copyNbt().getInt(key);
        return sweep < 0 || sweep > LAST_SWEEP ? 0 : sweep;
    }

    public static void storeSweep(ItemStack stack, String key, int sweep) {
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, current -> {
            NbtCompound base = current.copyNbt();
            base.putInt(key, sweep);
            return NbtComponent.of(base);
        });
    }
}
