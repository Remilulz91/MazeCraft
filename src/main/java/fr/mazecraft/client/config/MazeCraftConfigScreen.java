package fr.mazecraft.client.config;

import fr.mazecraft.config.MazeCraftConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config screen for MazeCraft. Only referenced when Cloth Config is loaded.
 */
public final class MazeCraftConfigScreen {

    private MazeCraftConfigScreen() { }

    public static Screen create(Screen parent) {
        MazeCraftConfig cfg = MazeCraftConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("config.mazecraft.title"))
                .setSavingRunnable(MazeCraftConfig::save);

        ConfigEntryBuilder entry = builder.entryBuilder();

        // === Generation category ===
        ConfigCategory gen = builder.getOrCreateCategory(Text.translatable("config.mazecraft.category.generation"));
        gen.addEntry(entry.startTextDescription(Text.translatable("config.mazecraft.weights.description")).build());
        gen.addEntry(entry.startIntField(Text.translatable("config.mazecraft.weightSmall"), cfg.weightSmall)
                .setMin(0).setMax(1000).setDefaultValue(45)
                .setSaveConsumer(v -> cfg.weightSmall = v).build());
        gen.addEntry(entry.startIntField(Text.translatable("config.mazecraft.weightMedium"), cfg.weightMedium)
                .setMin(0).setMax(1000).setDefaultValue(35)
                .setSaveConsumer(v -> cfg.weightMedium = v).build());
        gen.addEntry(entry.startIntField(Text.translatable("config.mazecraft.weightLarge"), cfg.weightLarge)
                .setMin(0).setMax(1000).setDefaultValue(15)
                .setSaveConsumer(v -> cfg.weightLarge = v).build());
        gen.addEntry(entry.startIntField(Text.translatable("config.mazecraft.weightColossal"), cfg.weightColossal)
                .setMin(0).setMax(1000).setDefaultValue(5)
                .setSaveConsumer(v -> cfg.weightColossal = v).build());

        // === Protection category ===
        ConfigCategory prot = builder.getOrCreateCategory(Text.translatable("config.mazecraft.category.protection"));
        prot.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.protectUntilSolved"), cfg.protectUntilSolved)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.mazecraft.protectUntilSolved.tooltip"))
                .setSaveConsumer(v -> cfg.protectUntilSolved = v).build());
        prot.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.preventWallWalking"), cfg.preventWallWalking)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.mazecraft.preventWallWalking.tooltip"))
                .setSaveConsumer(v -> cfg.preventWallWalking = v).build());

        // === Enemies category ===
        ConfigCategory enemies = builder.getOrCreateCategory(Text.translatable("config.mazecraft.category.enemies"));
        enemies.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.enableGuardians"), cfg.enableGuardians)
                .setDefaultValue(true).setTooltip(Text.translatable("config.mazecraft.enableGuardians.tooltip"))
                .setSaveConsumer(v -> cfg.enableGuardians = v).build());
        enemies.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.enableAmbushes"), cfg.enableAmbushes)
                .setDefaultValue(true).setTooltip(Text.translatable("config.mazecraft.enableAmbushes.tooltip"))
                .setSaveConsumer(v -> cfg.enableAmbushes = v).build());
        enemies.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.enableChampion"), cfg.enableChampion)
                .setDefaultValue(true).setTooltip(Text.translatable("config.mazecraft.enableChampion.tooltip"))
                .setSaveConsumer(v -> cfg.enableChampion = v).build());
        enemies.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.enablePatrols"), cfg.enablePatrols)
                .setDefaultValue(true).setTooltip(Text.translatable("config.mazecraft.enablePatrols.tooltip"))
                .setSaveConsumer(v -> cfg.enablePatrols = v).build());
        enemies.addEntry(entry.startIntField(Text.translatable("config.mazecraft.patrolIntervalSeconds"), cfg.patrolIntervalSeconds)
                .setMin(5).setMax(600).setDefaultValue(30)
                .setTooltip(Text.translatable("config.mazecraft.patrolIntervalSeconds.tooltip"))
                .setSaveConsumer(v -> cfg.patrolIntervalSeconds = v).build());
        enemies.addEntry(entry.startDoubleField(Text.translatable("config.mazecraft.enemyMultiplier"), cfg.enemyMultiplier)
                .setMin(0.0).setMax(5.0).setDefaultValue(1.0)
                .setTooltip(Text.translatable("config.mazecraft.enemyMultiplier.tooltip"))
                .setSaveConsumer(v -> cfg.enemyMultiplier = v).build());

        return builder.build();
    }
}
