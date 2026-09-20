package fr.mazecraft.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Golden particle trails of Ariadne's Thread, shown only to their owner for ~8 seconds. */
public final class ThreadTrails {

    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.8f, 0.2f), 1.0f);
    private static final int REFRESH_TICKS = 5;
    private static final int LIFETIME_TICKS = 160;

    private record Trail(List<Vec3d> points, long until) { }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private ThreadTrails() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TRAILS.isEmpty() || server.getTicks() % REFRESH_TICKS != 0) return;
            Iterator<Map.Entry<UUID, Trail>> it = TRAILS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Trail> e = it.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player == null || server.getTicks() > e.getValue().until()) {
                    it.remove();
                    continue;
                }
                ServerWorld world = player.getServerWorld();
                for (Vec3d p : e.getValue().points()) {
                    world.spawnParticles(player, GOLD, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRAILS.clear());
    }

    public static void show(ServerPlayerEntity player, List<Vec3d> points) {
        TRAILS.put(player.getUuid(), new Trail(points, player.getServer().getTicks() + LIFETIME_TICKS));
    }
}
