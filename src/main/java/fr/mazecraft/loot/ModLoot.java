package fr.mazecraft.loot;

import fr.mazecraft.item.ModItems;
import fr.mazecraft.structure.MazeStyle;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * The jungle fragment's temporary home.
 *
 * <p>Eight of the nine key fragments come out of a crypt under a building of this mod's own.
 * The ninth, the jungle's, has no building yet — and without it the key cannot be made and the
 * mod cannot be finished. So until that landmark exists the jungle fragment is added to the
 * vanilla jungle temple's chests.</p>
 *
 * <p><b>This is a stand-in and has to be taken out again.</b> The day the jungle landmark ships,
 * leaving this in place would make the one fragment that is farmable from an ordinary vanilla
 * structure, which is not the same game as the other eight.</p>
 */
public final class ModLoot {

    /** Where the jungle fragment is parked in the meantime. */
    private static final RegistryKey<net.minecraft.loot.LootTable> JUNGLE_TEMPLE =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.ofVanilla("chests/jungle_temple"));

    /**
     * Odds per chest. A jungle temple holds two, so a temple is about a two-in-three chance of
     * the fragment — findable without being handed over, which is roughly where the crypt of a
     * large maze sits.
     */
    private static final float CHANCE = 0.40f;

    private ModLoot() { }

    public static void register() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            // Vanilla's own copy only: a data pack that replaces the temple's table has said
            // what it wants in there, and a mod that bolts a pool back on is overruling it.
            if (source.isBuiltin() && JUNGLE_TEMPLE.equals(key)) {
                builder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(CHANCE))
                        .with(ItemEntry.builder(ModItems.KEY_FRAGMENTS.get(MazeStyle.JUNGLE))));
            }
        });
    }
}
