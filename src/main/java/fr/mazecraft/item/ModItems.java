package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import fr.mazecraft.entity.ModEntities;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {

    /** Ariadne's Thread: 8 uses, points to the nearest maze or traces the way inside one. */
    public static final Item ARIADNE_THREAD = Registry.register(Registries.ITEM, MazeCraft.id("ariadne_thread"),
            new AriadneThreadItem(new Item.Settings().maxDamage(AriadneThreadItem.USES).rarity(Rarity.UNCOMMON)));

    /** Minotaur Horn: dropped by the Minotaur, rallies nearby players (Strength + Speed). */
    public static final Item MINOTAUR_HORN = Registry.register(Registries.ITEM, MazeCraft.id("minotaur_horn"),
            new MinotaurHornItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final Item MINOTAUR_SPAWN_EGG = Registry.register(Registries.ITEM, MazeCraft.id("minotaur_spawn_egg"),
            new SpawnEggItem(ModEntities.MINOTAUR, 0x4A2C17, 0xD8C8A0, new Item.Settings()));

    private ModItems() { }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ARIADNE_THREAD);
            entries.add(MINOTAUR_HORN);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(MINOTAUR_SPAWN_EGG));
    }
}
