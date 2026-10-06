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

    /**
     * The three music discs. Their own items with their own songs — nothing vanilla is
     * overwritten: replacing a vanilla disc would change the base game for every world the
     * mod is ever installed in.
     */
    public static final Item MUSIC_DISC_FIL = Registry.register(Registries.ITEM, MazeCraft.id("music_disc_fil"),
            new Item(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.RARE)
                    .jukeboxPlayable(jukebox("fil"))));

    public static final Item MUSIC_DISC_AIRAIN = Registry.register(Registries.ITEM, MazeCraft.id("music_disc_airain"),
            new Item(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.RARE)
                    .jukeboxPlayable(jukebox("airain"))));

    public static final Item MUSIC_DISC_ASTERION = Registry.register(Registries.ITEM, MazeCraft.id("music_disc_asterion"),
            new Item(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC)
                    .jukeboxPlayable(jukebox("asterion"))));

    private static net.minecraft.registry.RegistryKey<net.minecraft.block.jukebox.JukeboxSong> jukebox(String name) {
        return net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.JUKEBOX_SONG,
                MazeCraft.id(name));
    }

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
            entries.add(MUSIC_DISC_FIL);
            entries.add(MUSIC_DISC_AIRAIN);
            entries.add(MUSIC_DISC_ASTERION);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(MINOTAUR_SPAWN_EGG));
    }
}
