package fr.mazecraft.item;

import fr.mazecraft.MazeCraft;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import fr.mazecraft.structure.MazeStyle;

import java.util.EnumMap;
import java.util.Map;
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

    /**
     * The nine key fragments, one per biome in {@link MazeStyle#FRAGMENT_STYLES}.
     *
     * <p>Nine items and not one, so the crafting grid can tell them apart — see
     * {@link KeyFragmentItem}. Registered from the list rather than written out nine times, so
     * adding a tenth biome to the key is one entry in that array.</p>
     */
    public static final Map<MazeStyle, Item> KEY_FRAGMENTS = registerFragments();

    private static Map<MazeStyle, Item> registerFragments() {
        Map<MazeStyle, Item> fragments = new EnumMap<>(MazeStyle.class);
        for (MazeStyle style : MazeStyle.FRAGMENT_STYLES) {
            fragments.put(style, Registry.register(Registries.ITEM,
                    MazeCraft.id("key_fragment_" + style.id()),
                    new KeyFragmentItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE), style)));
        }
        return java.util.Collections.unmodifiableMap(fragments);
    }

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

    /**
     * The mod's own tab in the creative inventory.
     *
     * <p>Everything was in vanilla's Tools &amp; Utilities before, which is where a mod's items go
     * when nobody has decided where they go: eighteen of ours scattered through four screens of
     * vanilla tools, in the order the registry happened to hand them over. A tab of our own puts
     * them together, in the order the player meets them — the two ways of finding a maze, then
     * the nine fragments, then what they make, then what the bosses leave behind — and it is also
     * the one place that tells a player at a glance what the mod actually adds.</p>
     *
     * <p>They are no longer listed in Tools &amp; Utilities as well. An item in two tabs is not
     * twice as findable, it is the same item twice; the mod's tab is the answer to "where are the
     * MazeCraft items" and vanilla's is the answer to "where are the shears". The Minotaur's
     * spawn egg is the exception and stays in Spawn Eggs too, because every spawn egg in the game
     * is there and a player looking for one looks there first.</p>
     */
    public static final RegistryKey<ItemGroup> GROUP =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, MazeCraft.id("mazecraft"));

    private ModItems() { }

    public static void register() {
        // The group's own entries, rather than an ItemGroupEvents hook: this is our tab, so what
        // is in it is part of building it. The hook is for adding to somebody else's.
        Registry.register(Registries.ITEM_GROUP, GROUP, FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.mazecraft"))
                .icon(() -> new ItemStack(KRONOS_KEY))
                .entries((context, entries) -> {
                    entries.add(ARIADNE_COMPASS);
                    entries.add(ARIADNE_THREAD);
                    for (MazeStyle style : MazeStyle.FRAGMENT_STYLES) entries.add(KEY_FRAGMENTS.get(style));
                    entries.add(KRONOS_KEY);
                    entries.add(MINOTAUR_HORN);
                    entries.add(ASTERION_HORN);
                    entries.add(MUSIC_DISC_FIL);
                    entries.add(MUSIC_DISC_AIRAIN);
                    entries.add(MUSIC_DISC_ASTERION);
                    entries.add(MINOTAUR_SPAWN_EGG);
                })
                .build());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(MINOTAUR_SPAWN_EGG));
    }
}
