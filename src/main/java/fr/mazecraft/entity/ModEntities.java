package fr.mazecraft.entity;

import fr.mazecraft.MazeCraft;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {

    /** The Minotaur: ~2.9 blocks tall, fits the 3-wide / 4-high corridors. */
    public static final EntityType<MinotaurEntity> MINOTAUR = Registry.register(Registries.ENTITY_TYPE, MazeCraft.id("minotaur"),
            EntityType.Builder.create(MinotaurEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.4f, 2.9f)
                    .eyeHeight(2.5f)
                    .maxTrackingRange(10)
                    .build("minotaur"));

    private ModEntities() { }

    public static void register() {
        FabricDefaultAttributeRegistry.register(MINOTAUR, MinotaurEntity.createMinotaurAttributes());
    }
}
