package fr.mazecraft.client.render;

import fr.mazecraft.MazeCraft;
import fr.mazecraft.entity.MinotaurEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Minotaur model: bull head with horns, broad torso, long arms, axe in the right hand.
 * Built at biped scale (32 px tall) and scaled x1.45 by the renderer (~2.9 blocks).
 * Texture: 128 × 64, see textures/entity/minotaur.png.
 */
public class MinotaurModel extends SinglePartEntityModel<MinotaurEntity> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(MazeCraft.id("minotaur"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MinotaurModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData head = root.addChild("head", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-4f, -8f, -4f, 8, 8, 8)            // skull
                        .uv(32, 0).cuboid(-3f, -4f, -7f, 6, 4, 3),           // snout
                ModelTransform.pivot(0f, 0f, -1f));
        head.addChild("right_horn", ModelPartBuilder.create()
                        .uv(52, 0).cuboid(-8f, -8f, -1f, 4, 2, 2)
                        .uv(64, 0).cuboid(-9f, -11f, -1f, 2, 3, 2),
                ModelTransform.NONE);
        head.addChild("left_horn", ModelPartBuilder.create()
                        .uv(52, 0).mirrored().cuboid(4f, -8f, -1f, 4, 2, 2)
                        .uv(64, 0).mirrored().cuboid(7f, -11f, -1f, 2, 3, 2),
                ModelTransform.NONE);

        root.addChild("body", ModelPartBuilder.create()
                        .uv(0, 16).cuboid(-5f, 0f, -3f, 10, 12, 6),
                ModelTransform.pivot(0f, 0f, 0f));

        ModelPartData rightArm = root.addChild("right_arm", ModelPartBuilder.create()
                        .uv(32, 16).cuboid(-4f, -2f, -2f, 4, 13, 4),
                ModelTransform.pivot(-5f, 2f, 0f));
        rightArm.addChild("axe", ModelPartBuilder.create()
                        .uv(72, 0).cuboid(-2.5f, 9.5f, -10f, 1, 1, 12, new Dilation(0f))  // handle
                        .uv(72, 16).cuboid(-3f, 6f, -11f, 2, 6, 3),                       // blade
                ModelTransform.NONE);

        root.addChild("left_arm", ModelPartBuilder.create()
                        .uv(32, 16).mirrored().cuboid(0f, -2f, -2f, 4, 13, 4),
                ModelTransform.pivot(5f, 2f, 0f));

        root.addChild("right_leg", ModelPartBuilder.create()
                        .uv(48, 16).cuboid(-2.5f, 0f, -2.5f, 5, 12, 5),
                ModelTransform.pivot(-2.5f, 12f, 0f));
        root.addChild("left_leg", ModelPartBuilder.create()
                        .uv(48, 16).mirrored().cuboid(-2.5f, 0f, -2.5f, 5, 12, 5),
                ModelTransform.pivot(2.5f, 12f, 0f));

        return TexturedModelData.of(data, 128, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(MinotaurEntity entity, float limbAngle, float limbDistance, float age,
                          float headYaw, float headPitch) {
        int state = entity.getState();

        head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        head.roll = 0f;

        float walk = MathHelper.cos(limbAngle * 0.6662f);
        rightLeg.pitch = walk * 1.2f * limbDistance;
        leftLeg.pitch = -walk * 1.2f * limbDistance;
        rightArm.pitch = -walk * 0.6f * limbDistance;
        leftArm.pitch = walk * 0.6f * limbDistance;
        rightArm.roll = 0.1f;
        leftArm.roll = -0.1f;
        rightArm.yaw = 0f;
        leftArm.yaw = 0f;

        switch (state) {
            case MinotaurEntity.STATE_WINDUP -> {
                // Rears up, both arms raised, head lowered: horns forward
                rightArm.pitch = -2.7f;
                leftArm.pitch = -2.7f;
                head.pitch = 0.5f;
            }
            case MinotaurEntity.STATE_CHARGING -> {
                head.pitch = 0.9f;
                rightArm.pitch = 0.6f;
                leftArm.pitch = 0.6f;
            }
            case MinotaurEntity.STATE_STUNNED -> {
                head.pitch = 0.6f;
                head.roll = MathHelper.sin(age * 0.4f) * 0.35f;
                rightArm.pitch = 0.25f;
                leftArm.pitch = 0.25f;
                rightArm.roll = 0.35f;
                leftArm.roll = -0.35f;
            }
            default -> { }
        }

        // Axe swing (melee)
        if (handSwingProgress > 0f) {
            float swing = MathHelper.sin(handSwingProgress * (float) Math.PI);
            rightArm.pitch = -2.2f + swing * 2.4f;
            rightArm.yaw = -0.2f * swing;
        }
    }
}
