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

    /** Last sweep offered: sweep 0 on the item's holder, then a ring of eight around them. */
    public static final int LAST_SWEEP = 8;

    /**
     * How far out the ring of eight sits, in units of one sweep's own reach.
     *
     * <p>1.75, and the value is load-bearing. At 2 the eight discs touch the middle one at a
     * single point each, which leaves a hole on all eight bisectors starting a hair outside the
     * middle sweep's rim: a maze sitting in one of those holes is missed by all nine uses. At
     * 1.75 there is no uncovered point within 2.35 sweeps — measured over the whole disc, not
     * reasoned about — and nothing past 2.75 sweeps is reached at all.</p>
     */
    private static final double RING = 1.75;

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
        double angle = Math.PI * 2.0 * (sweep - 1) / LAST_SWEEP;
        double out = reach * RING;
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
