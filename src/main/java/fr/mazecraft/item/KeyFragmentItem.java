package fr.mazecraft.item;

import fr.mazecraft.structure.MazeStyle;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A key fragment: the trophy for having cleared all three steps of one style.
 *
 * <p>Sixteen of them make the Key of Kronos (1.0.0). They are ordinary items — tradeable,
 * droppable, nice to line up in a display — because they are a collection, not a credential:
 * the door to Kronos reads the player's own progression, so handing a friend your fragments
 * gets them nothing.</p>
 */
public class KeyFragmentItem extends Item {

    private static final String STYLE_KEY = "MazeStyle";

    public KeyFragmentItem(Settings settings) {
        super(settings);
    }

    /** A fragment for this style. */
    public static ItemStack of(Item item, MazeStyle style) {
        ItemStack stack = new ItemStack(item);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(STYLE_KEY, style.id());
        stack.apply(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, current -> {
            NbtCompound merged = current.copyNbt();
            merged.copyFrom(nbt);
            return NbtComponent.of(merged);
        });
        return stack;
    }

    /** The style this fragment belongs to, or null if it carries none. */
    public static MazeStyle styleOf(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyNbt().getString(STYLE_KEY);
        return id.isEmpty() ? null : MazeStyle.fromId(id);
    }

    @Override
    public Text getName(ItemStack stack) {
        MazeStyle style = styleOf(stack);
        if (style == null) return super.getName(stack);
        return Text.translatable("item.mazecraft.key_fragment.styled",
                Text.translatable("mazecraft.style." + style.id()));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mazecraft.key_fragment.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
