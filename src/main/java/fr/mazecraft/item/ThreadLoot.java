package fr.mazecraft.item;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.entry.EmptyEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;

import java.util.Map;

/**
 * Ariadne's Thread in vanilla chests, so players can find their first maze.
 * Added through the Fabric event (never overwrites vanilla files): datapack / mod friendly.
 * Chance per chest in percent.
 */
public final class ThreadLoot {

    private static final Map<RegistryKey<LootTable>, Integer> CHANCES = Map.of(
            LootTables.SIMPLE_DUNGEON_CHEST, 15,
            LootTables.ABANDONED_MINESHAFT_CHEST, 8,
            LootTables.DESERT_PYRAMID_CHEST, 15,
            LootTables.JUNGLE_TEMPLE_CHEST, 20,
            LootTables.SHIPWRECK_MAP_CHEST, 20,
            LootTables.STRONGHOLD_CORRIDOR_CHEST, 15,
            LootTables.NETHER_BRIDGE_CHEST, 15,
            LootTables.BASTION_OTHER_CHEST, 10,
            LootTables.END_CITY_TREASURE_CHEST, 15
    );

    private ThreadLoot() { }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Integer chance = CHANCES.get(key);
            if (chance == null || !source.isBuiltin()) return;
            tableBuilder.pool(LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1))
                    .with(ItemEntry.builder(ModItems.ARIADNE_THREAD).weight(chance))
                    .with(EmptyEntry.builder().weight(100 - chance)));
        });
    }
}
