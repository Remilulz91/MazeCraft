package fr.mazecraft.block;

import fr.mazecraft.progression.VaultKeypad;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * One key of a vault's keypad: ten digits, a reset and a validate, carved into the wall.
 *
 * <p>A single block with a {@link #KEY} property rather than twelve blocks. The twelve are the
 * same object with a different face, and twelve registrations would mean twelve of everything
 * — loot tables, tags, item entries — for no gain.</p>
 *
 * <p>Deliberately not a block entity. What a player has typed so far is kept per player on the
 * server, not in the world: with one shared buffer in the block, two people at the same keypad
 * would overwrite each other's entry. Nothing here is saved — an interrupted entry is four
 * digits to type again.</p>
 */
public class CodeKeyBlock extends Block {

    /**
     * The keypad's alphabet: 0–9 are the digits, 10 clears the entry, 11 validates it, and
     * 12–15 are the tally faces (one bar to four) that a plaque in a dead end wears above its
     * digit to say which position of the code it is.
     *
     * <p>The plaques are the same block as the keys on purpose. A player who has seen the wall
     * of twelve recognises the thing on the dead-end wall instantly, and knows what to do with
     * it — which is the whole of the puzzle's teaching, done with no text.</p>
     */
    public static final IntProperty KEY = IntProperty.of("key", 0, 15);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public static final int RESET = 10, ENTER = 11;
    /** {@code SLOT + n} is the face showing n + 1 tally bars. */
    public static final int SLOT = 12;
    /** How many digits the code has — and so how many plaques there are. */
    public static final int SLOTS = 4;

    public CodeKeyBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(KEY, 0).with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(KEY, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    /**
     * A press.
     *
     * <p>Answered here rather than from an event so that one click is one press. The client
     * tries the main hand first and offers the off hand as well whenever the first attempt
     * comes back unaccepted — so a handler the client cannot run is a handler that gets asked
     * twice, and the keypad typed every digit twice. Returning {@code SUCCESS} on both sides
     * ends the interaction where it started.</p>
     */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity sender) {
            VaultKeypad.press(serverWorld, sender, pos, state.get(KEY), "block");
        }
        return ActionResult.SUCCESS;
    }
}
