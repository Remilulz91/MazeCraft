package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {

    /** Ariadne's Thread: 8 uses, points to the nearest maze or traces the way inside one. */
    public static final Item ARIADNE_THREAD = Registry.register(Registries.ITEM, MazeCraft.id("ariadne_thread"),
            new AriadneThreadItem(new Item.Settings().maxDamage(AriadneThreadItem.USES).rarity(Rarity.UNCOMMON)));

    private ModItems() { }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ARIADNE_THREAD));
    }
}
