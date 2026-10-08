package fr.mazecraft.progression;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.block.CodeKeyBlock;
import fr.mazecraft.block.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.util.ActionResult;
import fr.mazecraft.protection.MazeState;
import fr.mazecraft.structure.MazeFinder;
import fr.mazecraft.structure.MazePiece;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The keypad of a colossal maze's vault: twelve keys in a wall, four digits to find, one door.
 *
 * <p><b>What a player has typed is kept per player, on the server, and is never saved.</b> Per
 * player because two people at the same wall would otherwise overwrite each other's entry
 * keystroke by keystroke; on the server because a client that knows the buffer can be made to
 * reveal it; never saved because an interrupted entry is four digits to type again, and a
 * buffer on disk is a buffer that can disagree with the world.</p>
 *
 * <p>The door itself is the opposite — that <em>is</em> saved, in {@link MazeState}, per maze
 * and not per player. A block cannot be open for one person and shut for another, so whoever
 * types the code opens it for everybody, exactly as a lever does.</p>
 */
public final class VaultKeypad {

    /** How long a wrong code locks the keypad. */
    private static final int LOCKOUT_TICKS = 60; // 3 seconds

    /** What each player has typed so far, by player. Deliberately not saved. */
    private static final Map<UUID, int[]> TYPED = new HashMap<>();
    /** How many digits of {@link #TYPED} are filled, by player. */
    private static final Map<UUID, Integer> FILLED = new HashMap<>();
    /** World time each locked-out player may type again, by player. */
    private static final Map<UUID, Long> LOCKED = new HashMap<>();

    private VaultKeypad() { }

    /** The last press each player made: {tick, packed position}. Transient, like the entry. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    public static void register() {
        // Two ways in, on purpose. The block's own onUse is the one that should fire, and it is
        // the one that keeps a click to a single press. But this event fires earlier in the
        // chain, and when it answers, vanilla's path — onUse included — never runs at all.
        //
        // Which of the two actually gets there is not something the mod should have to be right
        // about: handling it from the block alone left the keypad completely dead, and handling
        // it from the event alone typed every digit twice. So both call the same entry point and
        // the entry point refuses to act twice for the same player, on the same block, in the
        // same tick. Whichever path arrives first does the press; the other finds it done.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld serverWorld)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(ModBlocks.CODE_KEY)) return ActionResult.PASS;
            press(serverWorld, serverPlayer, pos, state.get(CodeKeyBlock.KEY), "event");
            return ActionResult.SUCCESS;
        });
        MazeCraft.LOGGER.info("[MazeCraft] Vault keypad registered");
    }

    /**
     * True when this exact press has already been dealt with.
     *
     * <p>A right-click can reach the server more than once — the client offers the off hand when
     * the main hand comes back unaccepted — and it can reach this class by two routes. One press
     * per player, per block, per tick settles both. A tick is 50 ms; nobody clicks a wall twice
     * inside one, and the client's own use cooldown is four.</p>
     */
    private static boolean alreadyPressed(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        long tick = world.getTime(), at = pos.asLong();
        long[] last = LAST.get(player.getUuid());
        if (last != null && last[0] == tick && last[1] == at) return true;
        LAST.put(player.getUuid(), new long[]{tick, at});
        return false;
    }

    /**
     * One press, from {@link CodeKeyBlock#onUse}.
     *
     * <p><b>Why this is called from the block and not from a {@code UseBlockCallback}.</b> A
     * right-click is not one interaction, it is up to two: the client offers the main hand, and
     * only if that comes back unaccepted does it offer the off hand. A {@code UseBlockCallback}
     * registered in common code runs on the client too, and there it has no server world, so it
     * returned PASS — the client therefore believed nothing had happened, sent the off-hand
     * packet as well, and the server ran the press twice. One click, two digits. Answering from
     * the block's own {@code onUse} fixes it at the source: the client runs the same override,
     * sees the interaction accepted, and never offers the second hand.</p>
     */
    public static void press(ServerWorld world, ServerPlayerEntity player, BlockPos pos, int key,
                             String path) {
        if (alreadyPressed(world, player, pos)) return;
        MazeFinder.MazeHit found = MazeFinder.find(world, pos, 0);
        MazePiece piece = found != null ? found.piece() : null;
        // A maze from /maze debug place is not a structure, so nothing in the world can find
        // it. It is only usable while the session that placed it is still running — and only
        // for its OWN keys: without the containment test, placing a second maze would have the
        // first one's code judge the second one's keypad.
        if (piece == null) {
            MazePiece placed = placedMaze();
            if (placed != null && placed.isProtected(pos)) piece = placed;
        }
        // Never fail silently. Both ways of getting here used to return with no sound, no
        // message and no clue — which is indistinguishable, from the player's side, from the
        // keypad being broken.
        if (MazeCraft.isDebugBuild()) {
            MazeCraft.LOGGER.info("[MazeCraft] keypad press key={} at {} by the {} path — maze: {}",
                    key, pos.toShortString(), path,
                    found != null ? "natural" : piece != null ? "placed" : "NONE");
        }
        if (piece == null) {
            player.sendMessage(Text.translatable("mazecraft.vault.orphan")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (!piece.hasVault()) {
            player.sendMessage(Text.translatable("mazecraft.vault.orphan")
                    .formatted(Formatting.RED), true);
            return;
        }
        press(world, player, piece, found != null ? found.key() : null, pos, key);
    }

    /**
     * One press.
     *
     * @return the key pressed, or -1 when the block was not a key of this maze's keypad
     */
    private static int press(ServerWorld world, ServerPlayerEntity player, MazePiece piece,
                             Long mazeKey, BlockPos pos, int key) {
        // A plaque in a dead end is the same block as a key, on purpose — it teaches the player
        // what to do with it. It is not part of the keypad, so pressing it does nothing but say
        // what it is.
        if (!isKeypadKey(piece, pos, key)) {
            if (key >= CodeKeyBlock.SLOT) {
                player.sendMessage(Text.translatable("mazecraft.vault.plaque_slot",
                        key - CodeKeyBlock.SLOT + 1).formatted(Formatting.GOLD), true);
            } else {
                player.sendMessage(Text.translatable("mazecraft.vault.plaque_digit", key)
                        .formatted(Formatting.GOLD), true);
            }
            return key;
        }

        MazeState state = MazeState.get(world);
        // An open vault takes no more codes. Asked of the door itself as well as of the saved
        // state: the state is keyed on the structure, so a maze from /maze debug place has no
        // entry and used to let the code be typed again and the opening replayed, message,
        // sound and all.
        if ((mazeKey != null && state.isVaultOpen(mazeKey)) || piece.isVaultDoorOpen(world)) {
            player.sendMessage(Text.translatable("mazecraft.vault.already_open")
                    .formatted(Formatting.GRAY), true);
            return key;
        }

        UUID id = player.getUuid();
        Long until = LOCKED.get(id);
        if (until != null && world.getTime() < until) {
            long left = (until - world.getTime() + 19) / 20;
            player.sendMessage(Text.translatable("mazecraft.vault.locked", left)
                    .formatted(Formatting.RED), true);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.BLOCKS, 0.6f, 0.5f);
            return key;
        }
        LOCKED.remove(id);

        int[] typed = TYPED.computeIfAbsent(id, k -> new int[CodeKeyBlock.SLOTS]);
        int filled = FILLED.getOrDefault(id, 0);

        if (key == CodeKeyBlock.RESET) {
            FILLED.put(id, 0);
            click(world, pos, 0.6f);
            show(player, typed, 0);
            return key;
        }
        if (key == CodeKeyBlock.ENTER) {
            if (filled < CodeKeyBlock.SLOTS) {
                player.sendMessage(Text.translatable("mazecraft.vault.incomplete",
                        CodeKeyBlock.SLOTS).formatted(Formatting.RED), true);
                click(world, pos, 0.5f);
                return key;
            }
            validate(world, player, piece, mazeKey, pos, typed);
            return key;
        }

        if (filled >= CodeKeyBlock.SLOTS) {
            // Full. Rather than silently swallow the press, say so — the row has no display of
            // its own, so the player's only feedback is this line.
            player.sendMessage(Text.translatable("mazecraft.vault.full").formatted(Formatting.RED), true);
            click(world, pos, 0.5f);
            return key;
        }
        typed[filled] = key;
        FILLED.put(id, filled + 1);
        // Rising pitch with the digit, so the wall answers differently to every key.
        click(world, pos, 0.8f + key * 0.08f);
        show(player, typed, filled + 1);
        return key;
    }

    private static void validate(ServerWorld world, ServerPlayerEntity player, MazePiece piece,
                                 Long mazeKey, BlockPos pos, int[] typed) {
        int[] code = piece.vaultCode();
        boolean right = true;
        for (int i = 0; i < code.length && right; i++) right = typed[i] == code[i];

        UUID id = player.getUuid();
        FILLED.put(id, 0);

        if (!right) {
            LOCKED.put(id, world.getTime() + LOCKOUT_TICKS);
            player.sendMessage(Text.translatable("mazecraft.vault.wrong", LOCKOUT_TICKS / 20)
                    .formatted(Formatting.RED), false);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.BLOCKS, 1.0f, 0.5f);
            world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.01);
            return;
        }

        // Saved first: if the blocks came down and the save did not happen, the next repair
        // pass would put the door back and the code would have to be typed again.
        if (mazeKey != null) MazeState.get(world).openVault(mazeKey);
        piece.openVault(world);
        player.sendMessage(Text.translatable("mazecraft.vault.opened")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                SoundCategory.BLOCKS, 1.0f, 1.5f);
        BlockPos door = piece.vaultDoorBlocks().isEmpty() ? pos : piece.vaultDoorBlocks().get(0);
        world.playSound(null, door, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.4f, 0.4f);
        world.spawnParticles(ParticleTypes.END_ROD, door.getX() + 0.5, door.getY() + 1.0,
                door.getZ() + 0.5, 40, 1.2, 1.2, 0.4, 0.02);
        MazeProgress.grant(player, "open_vault");
    }

    /** True when this block is one of the twelve keys of this maze's keypad. */
    private static boolean isKeypadKey(MazePiece piece, BlockPos pos, int key) {
        if (key > CodeKeyBlock.ENTER) return false;
        BlockPos expected = piece.codeKeyPos(key);
        return expected != null && expected.equals(pos);
    }

    private static void click(ServerWorld world, BlockPos pos, float pitch) {
        world.playSound(null, pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,
                SoundCategory.BLOCKS, 0.8f, pitch);
    }

    /** The entry so far, in the action bar: the row of keys has no display of its own. */
    private static void show(ServerPlayerEntity player, int[] typed, int filled) {
        StringBuilder entry = new StringBuilder();
        for (int i = 0; i < CodeKeyBlock.SLOTS; i++) {
            if (i > 0) entry.append(' ');
            entry.append(i < filled ? Integer.toString(typed[i]) : "_");
        }
        player.sendMessage(Text.translatable("mazecraft.vault.entry", entry.toString())
                .formatted(filled == CodeKeyBlock.SLOTS ? Formatting.GOLD : Formatting.AQUA), true);
    }

    /**
     * The maze from {@code /maze debug place}, in a debug build only.
     *
     * <p>Such a maze is not a structure, so nothing in the world can find it — and without
     * this, placing a colossal maze to look at the vault would give you a keypad that does
     * nothing when you press it.</p>
     */
    private static MazePiece placedMaze() {
        return MazeCraft.isDebugBuild() ? fr.mazecraft.commands.DebugCommand.lastPlaced() : null;
    }

    /** Called when a player leaves: nothing of theirs should outlive them here. */
    public static void forget(UUID player) {
        TYPED.remove(player);
        FILLED.remove(player);
        LOCKED.remove(player);
    }
}
