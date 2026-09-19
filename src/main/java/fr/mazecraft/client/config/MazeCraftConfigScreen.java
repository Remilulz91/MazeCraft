package fr.mazecraft.client.config;

import fr.mazecraft.MazeCraft;
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

        // === Debug category ===
        ConfigCategory debug = builder.getOrCreateCategory(Text.translatable("config.mazecraft.category.debug"));
        if (MazeCraft.isDebugBuild()) {
            debug.addEntry(entry.startBooleanToggle(Text.translatable("config.mazecraft.enableDebugCommands"), cfg.enableDebugCommands)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.mazecraft.enableDebugCommands.tooltip"))
                    .setSaveConsumer(v -> cfg.enableDebugCommands = v)
                    .build());
        } else {
            debug.addEntry(entry.startTextDescription(Text.translatable("config.mazecraft.publicBuildNotice")).build());
        }

        return builder.build();
    }
}
