package fr.mazecraft.block;

import fr.mazecraft.progression.MazeBarrier;
import fr.mazecraft.structure.MazeSize;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The sealed gateway: the shimmering plane that closes the entrance of a maze whose previous
 * step the player has not cleared yet.
 *
 * <p>The block itself has <b>no collision</b>, exactly like a nether portal. Minecraft has no
 * notion of a block that is solid for one player and not another, and faking one with
 * per-player block packets produces ghost blocks and players suffocating inside walls. So the
 * block is only the visible signal; who may pass is decided in {@link MazeBarrier}:</p>
 * <ul>
 *   <li>{@link #onEntityCollision} pushes back a player who has not earned the way through;</li>
 *   <li>a periodic sweep catches anyone who got through anyway — a one-block-thick plane with
 *       no collision can be crossed in a single tick at elytra speed, so the sweep, not this
 *       block, is what actually guarantees the gate.</li>
 * </ul>
 *
 * <p>Mobs, items and projectiles pass freely: only players are gated.</p>
 */
public class SealedGatewayBlock extends Block {

    /** Horizontal axis the plane runs along — X for north/south entrances, Z for east/west. */
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    /** Which step this gateway guards; drives the colour. */
    public static final EnumProperty<Tier> TIER = EnumProperty.of("tier", Tier.class);

    private static final VoxelShape X_SHAPE = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public enum Tier implements StringIdentifiable {
        MEDIUM("medium"),
        LARGE("large");

        private final String name;

        Tier(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        /** The gateway tier guarding a maze of this size, or null when the step is always open. */
        public static Tier of(MazeSize size) {
            return switch (size.step()) {
                case MEDIUM -> MEDIUM;
                case LARGE -> LARGE;
                default -> null; // small mazes are the entry step: never sealed
            };
        }
    }

    public SealedGatewayBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X).with(TIER, Tier.MEDIUM));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TIER);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        MazeBarrier.onTouch(world, pos, entity);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(4) != 0) return;
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        // Drift along the plane, never across it, so the surface reads as a membrane.
        double driftX = state.get(AXIS) == Direction.Axis.X ? (random.nextDouble() - 0.5) * 0.4 : 0.0;
        double driftZ = state.get(AXIS) == Direction.Axis.Z ? (random.nextDouble() - 0.5) * 0.4 : 0.0;
        world.addParticle(ParticleTypes.PORTAL, x, y, z, driftX, random.nextDouble() * 0.2, driftZ);
    }
}
