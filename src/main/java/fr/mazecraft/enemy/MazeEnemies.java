package fr.mazecraft.enemy;

import fr.mazecraft.structure.MazeStyle;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;

import java.util.List;
import java.util.Optional;

/**
 * Enemy tables per maze style, and helpers to spawn / equip them.
 * Used for guardians (placed at generation), lever ambushes and the final champion.
 */
public final class MazeEnemies {

    /** Command tag carried by maze champions (boss bar, advancement). */
    public static final String CHAMPION_TAG = "mazecraft_champion";
    /** Command tag prefix linking a champion to its maze: prefix + maze key. */
    public static final String MAZE_TAG_PREFIX = "mazecraft_maze_";
    /** Command tag carried by patrol mobs (used to cap how many are alive). */
    public static final String PATROL_TAG = "mazecraft_patrol";

    private MazeEnemies() { }

    /** Mob pool of a style (uniform pick). */
    public static List<EntityType<? extends MobEntity>> pool(MazeStyle style) {
        return switch (style) {
            case HEDGE, CHERRY, SAVANNA, TAIGA -> List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);
            case DARK_FOREST -> List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.WITCH);
            case DESERT, BADLANDS -> List.of(EntityType.HUSK, EntityType.SKELETON, EntityType.SPIDER);
            case SNOW -> List.of(EntityType.STRAY, EntityType.ZOMBIE);
            case JUNGLE -> List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.CAVE_SPIDER);
            case SWAMP -> List.of(EntityType.BOGGED, EntityType.SLIME, EntityType.ZOMBIE);
            case FORTRESS -> List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE);
            case CRIMSON -> List.of(EntityType.HOGLIN, EntityType.PIGLIN_BRUTE);
            case WARPED -> List.of(EntityType.ENDERMAN, EntityType.WITHER_SKELETON);
            case SOUL -> List.of(EntityType.WITHER_SKELETON, EntityType.SKELETON);
            case BASALT -> List.of(EntityType.MAGMA_CUBE, EntityType.BLAZE);
            case END -> List.of(EntityType.ENDERMITE, EntityType.SHULKER, EntityType.ENDERMAN);
        };
    }

    /** Champion type of a style (humanoid when possible, so it shows its gear). */
    public static EntityType<? extends MobEntity> championType(MazeStyle style) {
        return switch (style) {
            case HEDGE, CHERRY, SAVANNA, TAIGA, JUNGLE -> EntityType.ZOMBIE;
            case DARK_FOREST -> EntityType.VINDICATOR;
            case DESERT, BADLANDS -> EntityType.HUSK;
            case SNOW -> EntityType.STRAY;
            case SWAMP -> EntityType.BOGGED;
            case CRIMSON -> EntityType.PIGLIN_BRUTE;
            case FORTRESS, WARPED, SOUL, BASALT -> EntityType.WITHER_SKELETON;
            case END -> EntityType.ENDERMAN;
        };
    }

    /**
     * Creates, initializes and spawns a mob at (x+0.5, y, z+0.5).
     *
     * @param tier 0 = no armor, 1 = leather, 2 = iron, 3 = diamond (humanoids only)
     */
    public static MobEntity spawn(ServerWorldAccess world, EntityType<? extends MobEntity> type, BlockPos pos,
                                  int tier, boolean persistent, SpawnReason reason) {
        ServerWorld server = world.toServerWorld();
        MobEntity mob = type.create(server);
        if (mob == null) return null;
        mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                world.getRandom().nextFloat() * 360f, 0f);
        mob.initialize(world, world.getLocalDifficulty(pos), reason, null);
        if (tier > 0) {
            equip(mob, tier);
        } else if (wearsArmor(mob)) {
            // No armor, but always a helmet: zombies / skeletons must not burn in daylight
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.05f);
        }
        if (persistent) mob.setPersistent();
        world.spawnEntityAndPassengers(mob);
        return mob;
    }

    private static boolean wearsArmor(MobEntity mob) {
        return mob instanceof ZombieEntity || mob instanceof AbstractSkeletonEntity
                || mob instanceof AbstractPiglinEntity || mob instanceof VindicatorEntity;
    }

    private static void equip(MobEntity mob, int tier) {
        if (!wearsArmor(mob)) return;
        Item[] set = switch (tier) {
            case 1 -> new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
            case 2 -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            default -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        };
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            mob.equipStack(slots[i], new ItemStack(set[i]));
            mob.setEquipmentDropChance(slots[i], 0.05f); // rare drop: no free armor farm
        }
    }

    /** Turns a freshly spawned mob into a champion: name, extra health, enchanted gear (scaled by maze size). */
    public static void makeChampion(ServerWorld world, MobEntity mob, fr.mazecraft.structure.MazeSize size) {
        mob.addCommandTag(CHAMPION_TAG);
        mob.setCustomName(Text.translatable("mazecraft.champion.name").formatted(Formatting.GOLD, Formatting.BOLD));
        mob.setCustomNameVisible(true);
        mob.setPersistent();

        EntityAttributeInstance health = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * size.championHealth());
            mob.setHealth(mob.getMaxHealth());
        }

        if (wearsArmor(mob)) {
            Random random = world.getRandom();
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            boolean diamond = size.championDiamond();
            Item weapon = mob instanceof AbstractSkeletonEntity && mob.getType() != EntityType.WITHER_SKELETON
                    ? Items.BOW : (diamond ? Items.DIAMOND_SWORD : Items.IRON_SWORD);
            Item[] items = diamond
                    ? new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, weapon}
                    : new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, weapon};
            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = net.minecraft.enchantment.EnchantmentHelper.enchant(
                        random, new ItemStack(items[i]), size.championEnchantLevel(), world.getRegistryManager(), Optional.empty());
                mob.equipStack(slots[i], stack);
                mob.setEquipmentDropChance(slots[i], 0.25f);
            }
        }
    }
}
