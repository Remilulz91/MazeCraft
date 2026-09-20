package fr.mazecraft.entity;

import fr.mazecraft.MazeCraft;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * MazeCraft sound events. They point to vanilla sounds in assets/mazecraft/sounds.json
 * (pitched down ravager, music disc "5"...), so a resource pack can replace any of them
 * with real custom audio without touching the code.
 */
public final class ModSounds {

    public static final SoundEvent MINOTAUR_AMBIENT = register("entity.minotaur.ambient");
    public static final SoundEvent MINOTAUR_HURT = register("entity.minotaur.hurt");
    public static final SoundEvent MINOTAUR_DEATH = register("entity.minotaur.death");
    public static final SoundEvent MINOTAUR_STEP = register("entity.minotaur.step");
    public static final SoundEvent MINOTAUR_ROAR = register("entity.minotaur.roar");
    public static final SoundEvent MINOTAUR_CHARGE_HIT = register("entity.minotaur.charge_hit");
    public static final SoundEvent MINOTAUR_STUNNED = register("entity.minotaur.stunned");
    public static final SoundEvent MINOTAUR_HORN = register("item.minotaur_horn.use");

    public static final Identifier MUSIC_MINOTAUR_ID = MazeCraft.id("music.minotaur");
    public static final RegistryEntry.Reference<SoundEvent> MUSIC_MINOTAUR =
            Registry.registerReference(Registries.SOUND_EVENT, MUSIC_MINOTAUR_ID, SoundEvent.of(MUSIC_MINOTAUR_ID));

    private ModSounds() { }

    private static SoundEvent register(String path) {
        Identifier id = MazeCraft.id(path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Forces class loading (and therefore registration). */
    public static void register() { }
}
