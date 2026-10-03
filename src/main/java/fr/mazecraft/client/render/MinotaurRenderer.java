package fr.mazecraft.client.render;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.entity.MinotaurEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class MinotaurRenderer extends MobEntityRenderer<MinotaurEntity, MinotaurModel> {

    private static final Identifier TEXTURE = MazeCraft.id("textures/entity/minotaur.png");

    /**
     * The Minotaur of Kronos. Same model, same bones, same animations — a different hide: the
     * brown of a beast replaced by the slate of the tomb it has been shut in, horns and hooves
     * gone to verdigris like everything else down there, and the eyes left burning because on
     * a slate-coloured animal a brown eye disappears.
     */
    private static final Identifier KRONOS_TEXTURE = MazeCraft.id("textures/entity/minotaur_kronos.png");

    private static final float SCALE = 1.45f;
    /** Kronos stands a head taller. Rendering only — its hitbox is the Minotaur's. */
    private static final float KRONOS_SCALE = 1.75f;

    public MinotaurRenderer(EntityRendererFactory.Context context) {
        super(context, new MinotaurModel(context.getPart(MinotaurModel.LAYER)), 1.0f);
    }

    @Override
    protected void scale(MinotaurEntity entity, MatrixStack matrices, float amount) {
        float scale = entity.isKronos() ? KRONOS_SCALE : SCALE;
        matrices.scale(scale, scale, scale);
    }

    @Override
    public Identifier getTexture(MinotaurEntity entity) {
        return entity.isKronos() ? KRONOS_TEXTURE : TEXTURE;
    }
}
