package fr.mazecraft.item;

import fr.mazecraft.structure.MazeStyle;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A key fragment: one biome's share of the Key of Kronos.
 *
 * <p>There are nine, and they are nine separate items rather than one item carrying the biome
 * in a component. That is not a style choice, it is what makes the recipe possible: a crafting
 * ingredient matches by item id and cannot read a component, so a grid of nine fragments that
 * differed only by their data would accept nine copies of the same one. This way the recipe is
 * an ordinary shapeless one — order-free by definition, and impossible to satisfy with
 * duplicates.</p>
 *
 * <p>They stay ordinary items: tradeable, droppable, nice to line up in a frame. A spare is
 * worth something, because it is the one somebody else is missing.</p>
 */
public class KeyFragmentItem extends Item {

    private final MazeStyle style;

    public KeyFragmentItem(Settings settings, MazeStyle style) {
        super(settings);
        this.style = style;
    }

    /** The biome this fragment belongs to. */
    public MazeStyle style() {
        return style;
    }

    /** The style of this stack's fragment, or null if the stack is not a fragment at all. */
    public static MazeStyle styleOf(ItemStack stack) {
        return stack.getItem() instanceof KeyFragmentItem fragment ? fragment.style() : null;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.mazecraft.key_fragment.styled",
                Text.translatable("mazecraft.style." + style.id()));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mazecraft.key_fragment.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
