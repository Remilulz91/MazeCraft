package fr.mazecraft.structure;

import fr.mazecraft.MazeCraft;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.Optional;

/**
 * Hand-built pieces, loaded from {@code .nbt} templates rather than written in code.
 *
 * <p>The maze itself is procedural and will stay that way: it is a grid, its rules are
 * arithmetic, and arithmetic can be proved. A pagoda is not. Architecture is judged by looking
 * at it, which is the one thing this side of the project cannot do — so the designed pieces are
 * built in game, saved as templates, and only placed from here.</p>
 *
 * <p>Templates live in {@code data/mazecraft/structure/} — <b>singular</b>. The structure block
 * writes what it saves to {@code <world>/generated/<namespace>/structures/}, plural, and the two
 * are not the same path: {@code StructureTemplateManager} holds both constants and reads a data
 * pack through {@code new ResourceFinder("structure", ".nbt")}. Copying a saved file into the
 * mod means renaming the folder on the way. Getting it wrong returns an empty Optional and no
 * error at all, which is why {@link #place} says so in the log.</p>
 */
public final class MazeTemplates {

    /**
     * A template and the direction its front faces as it was built.
     *
     * <p>The facing is a property of the building, not of the code: whoever built it chose
     * which way the door looks, and the only way to know is to place it unrotated and see. The
     * pagoda came out facing south. Recording it here means every caller can ask for the
     * direction it wants and never think about rotations again.</p>
     */
    public record Piece(Identifier id, Direction facing) { }

    public static final Piece CHERRY_PAGODA =
            new Piece(MazeCraft.id("japanese_cherry_pagoda"), Direction.SOUTH);

    private MazeTemplates() { }

    /**
     * The rotation that turns a piece's built-in facing towards {@code desired}.
     *
     * <p>Found by asking each of the four rotations what it does, rather than by writing down
     * a table of which one maps south to west. A hand-written table was wrong on the first
     * attempt, and a rotation table is wrong in a way that looks plausible in game — the
     * building still appears, just facing a wall.</p>
     */
    public static BlockRotation rotationTo(Direction from, Direction to) {
        for (BlockRotation rotation : BlockRotation.values()) {
            if (rotation.rotate(from) == to) return rotation;
        }
        return BlockRotation.NONE;
    }

    /** The piece's footprint once rotated, x and z swapped for a quarter turn. */
    public static Vec3i size(ServerWorld world, Piece piece, BlockRotation rotation) {
        Optional<StructureTemplate> found = world.getStructureTemplateManager().getTemplate(piece.id());
        if (found.isEmpty()) return Vec3i.ZERO;
        Vec3i size = found.get().getSize();
        boolean quarter = rotation == BlockRotation.CLOCKWISE_90
                || rotation == BlockRotation.COUNTERCLOCKWISE_90;
        return quarter ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
    }

    /**
     * One placement of one piece: the template, how it is being turned, and where its own
     * coordinate (0,0,0) lands.
     *
     * <p>This exists so that anything built <em>alongside</em> a template — the shaft below the
     * pagoda, for one — can be written in the template's own coordinates and put through the
     * very same transformation the blocks of the template go through. Not a parallel copy of the
     * rotation arithmetic: {@link #world} calls {@code StructureTemplate.transform}, the same
     * static method {@code process()} calls for every block it lays. Alignment is then true by
     * construction rather than by agreement between two pieces of code that happen to match
     * today.</p>
     */
    public record Placement(StructureTemplate template, StructurePlacementData data, BlockPos origin) {

        /** Where the template's own {@code (x,y,z)} lands in the world, rotation included. */
        public BlockPos world(int x, int y, int z) {
            return StructureTemplate.transform(data, new BlockPos(x, y, z)).add(origin);
        }
    }

    /** A piece turned a given way, before anything has been said about where it goes. */
    private record Shape(StructureTemplate template, StructurePlacementData data, BlockPos middle) { }

    private static Shape shape(StructureTemplateManager templates, Piece piece, Direction facing) {
        Optional<StructureTemplate> found = templates.getTemplate(piece.id());
        if (found.isEmpty()) {
            MazeCraft.LOGGER.warn("[MazeCraft] no template {} in data/mazecraft/structure/", piece.id());
            return null;
        }
        StructureTemplate template = found.get();

        // Rotate the piece about its own middle, not about its corner.
        //
        // The pivot has to go in setPosition(). It does NOT go in the third argument of
        // place(), which is named "pivot" and is not the pivot: the blocks are laid out by
        // process(), which calls transform(placementData, pos) and takes the pivot from
        // placementData.getPosition(). Left unset it is (0,0,0), so a quarter turn swings the
        // whole footprint into another quadrant and the building lands beside the spot it was
        // asked for — behind the player, in the test that caught this. Passing the middle as
        // the third argument looked like it fixed that and changed nothing at all, because
        // that argument is only read for the processors.
        //
        // With the pivot on the middle, the middle is a fixed point of the rotation, so
        // subtracting it from the target puts it exactly on the target whichever way the piece
        // is turned.
        Vec3i size = template.getSize();
        BlockPos middle = new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
        StructurePlacementData data = new StructurePlacementData()
                .setRotation(rotationTo(piece.facing(), facing))
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(true)
                .setPosition(middle);
        return new Shape(template, data, middle);
    }

    /**
     * Works out where a piece goes, without placing it: centred on {@code at}, front turned
     * towards {@code facing}. Null when there is no such template.
     */
    public static Placement placement(StructureTemplateManager templates, Piece piece,
                                      BlockPos at, Direction facing) {
        Shape shape = shape(templates, piece, facing);
        return shape == null ? null
                : new Placement(shape.template(), shape.data(), at.subtract(shape.middle()));
    }

    public static Placement placement(ServerWorld world, Piece piece, BlockPos at, Direction facing) {
        return placement(world.getStructureTemplateManager(), piece, at, facing);
    }

    /**
     * Read the other way round: where to centre the piece so that its own cell {@code (tx, tz)}
     * lands on {@code target}.
     *
     * <p>Worldgen needs this direction, because what has to line up is not the middle of the
     * building. The staircase inside the pagoda is at template x 12 of 26, half a block west of
     * the 13 the piece is centred on, so the foot of the stairs swings a block as the building
     * turns — 101, 100, 99, 100 over the four facings, measured. The maze's plaza is where the
     * stairs have to come out, so the plaza is the fixed point and the building is placed around
     * it, not the reverse. Centring the pagoda on the plaza would miss by that one block, which
     * is exactly the kind of error that looks like it works until someone walks into a wall.</p>
     *
     * <p>{@code target}'s own Y is kept: this answers the horizontal question only.</p>
     */
    public static BlockPos centreFor(StructureTemplateManager templates, Piece piece,
                                     Direction facing, int tx, int tz, BlockPos target) {
        Shape shape = shape(templates, piece, facing);
        if (shape == null) return target;
        // world = transform(data, local) + (at - middle), so at = target - transform + middle.
        BlockPos turned = StructureTemplate.transform(shape.data(), new BlockPos(tx, 0, tz));
        return new BlockPos(
                target.getX() - turned.getX() + shape.middle().getX(),
                target.getY(),
                target.getZ() - turned.getZ() + shape.middle().getZ());
    }

    /** Places the piece centred on {@code at}, with its front turned towards {@code facing}. */
    public static boolean place(ServerWorld world, Piece piece, BlockPos at, Direction facing) {
        Placement placement = placement(world, piece, at, facing);
        return placement != null && place(world, placement, world.getRandom(), null);
    }

    /**
     * Places an already-computed placement, writing only inside {@code limit}.
     *
     * <p>{@code limit} is how a template survives being generated one chunk at a time.
     * {@code place} is all-or-nothing by nature — it lays its whole box in one call — and
     * writing outside the chunk currently being generated is not merely wasteful, it reaches
     * into chunks that are not there yet. Given a bounding box it skips every position outside
     * it, so the same call can be made once per chunk and each one writes only its own share.
     * Null means no limit, which is right for a command run on a loaded world and wrong during
     * generation.</p>
     */
    public static boolean place(net.minecraft.world.ServerWorldAccess world, Placement placement,
                                net.minecraft.util.math.random.Random random,
                                net.minecraft.util.math.BlockBox limit) {
        StructurePlacementData data = limit == null ? placement.data()
                : placement.data().copy().setBoundingBox(limit);
        return placement.template().place(world, placement.origin(), data.getPosition(), data,
                random, Block.NOTIFY_LISTENERS);
    }
}
