package fr.mazecraft.client.render;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.entity.MinotaurEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class MinotaurRenderer extends MobEntityRenderer<MinotaurEntity, MinotaurModel> {

    private static final Identifier TEXTURE = MazeCraft.id("textures/entity/minotaur.png");
    private static final float SCALE = 1.45f;

    public MinotaurRenderer(EntityRendererFactory.Context context) {
        super(context, new MinotaurModel(context.getPart(MinotaurModel.LAYER)), 1.0f);
    }

    @Override
    protected void scale(MinotaurEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTexture(MinotaurEntity entity) {
        return TEXTURE;
    }
}
