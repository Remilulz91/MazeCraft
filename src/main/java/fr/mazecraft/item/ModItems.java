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

    /** Ariadne's Compass: locates the maze the holder has to clear next (per-player progression). */
    public static final Item ARIADNE_COMPASS = Registry.register(Registries.ITEM, MazeCraft.id("ariadne_compass"),
            new AriadneCompassItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    /** Key fragment: one per style fully cleared; sixteen of them open the way to Kronos. */
    public static final Item KEY_FRAGMENT = Registry.register(Registries.ITEM, MazeCraft.id("key_fragment"),
            new KeyFragmentItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

    /** Key of Kronos: granted with the sixteenth fragment, bound to the player who earned it. */
    public static final Item KRONOS_KEY = Registry.register(Registries.ITEM, MazeCraft.id("kronos_key"),
            new KronosKeyItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    /** Minotaur Horn: dropped by the Minotaur, rallies nearby players (Strength + Speed). */
    public static final Item MINOTAUR_HORN = Registry.register(Registries.ITEM, MazeCraft.id("minotaur_horn"),
            new MinotaurHornItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final Item ASTERION_HORN = Registry.register(Registries.ITEM, MazeCraft.id("asterion_horn"),
            new AsterionHornItem(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC)
                    .fireproof()));

    public static final Item MINOTAUR_SPAWN_EGG = Registry.register(Registries.ITEM, MazeCraft.id("minotaur_spawn_egg"),
            new SpawnEggItem(ModEntities.MINOTAUR, 0x4A2C17, 0xD8C8A0, new Item.Settings()));

    private ModItems() { }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ARIADNE_THREAD);
            entries.add(ARIADNE_COMPASS);
            entries.add(KEY_FRAGMENT);
            entries.add(KRONOS_KEY);
            entries.add(MINOTAUR_HORN);
            entries.add(ASTERION_HORN);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(MINOTAUR_SPAWN_EGG));
    }
}
