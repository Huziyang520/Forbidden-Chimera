package com.huziyang520.forbiddenchimera.client;

import com.huziyang520.forbiddenchimera.entity.CreeperSkullProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Renderer for both creeper skull missiles, following vanilla {@code WitherSkullRenderer}.
 *
 * <p>Why not {@code ThrownItemRenderer}: that draws the skull as an <b>item</b> in the ground display
 * transform, so the head neither points along its flight direction nor lines up with the entity's
 * rotation - the player saw the head arriving sideways. Vanilla wither skulls are drawn with the real
 * skull model driven by {@code entity.getYRot(partial)} / {@code getXRot(partial)}, which is exactly
 * what makes a head face the way it flies (and therefore face the player it homed in on).
 *
 * <p>The lightning variant additionally re-submits the same model through
 * {@code RenderTypes.energySwirl(creeper_armor.png, u, v)} - a verbatim copy of vanilla
 * {@code CreeperPowerLayer}/{@code EnergySwirlLayer}: texture
 * {@code textures/entity/creeper/creeper_armor.png}, tint {@code -8355712}, both uv offsets scrolling
 * at {@code age * 0.01}. Same recipe as the mob's per-bone {@code LightningOverlayGeoLayer}, so the
 * missile and its shooter look like the same charged material.
 */
public class ChimeraSkullRenderer
        extends EntityRenderer<CreeperSkullProjectile, ChimeraSkullRenderer.SkullRenderState> {

    /** Vanilla skull skin for the creeper type ({@code SkullBlockRenderer#SKIN_BY_TYPE}). */
    private static final Identifier CREEPER_SKIN =
            Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png");
    /** Vanilla charged creeper overlay ({@code CreeperPowerLayer#POWER_LOCATION}). */
    private static final Identifier POWER_LOCATION =
            Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    /** Vanilla scroll speed: {@code t * 0.01} on both uv axes. */
    private static final float SCROLL_PER_TICK = 0.01F;
    /** Vanilla overlay tint from {@code EnergySwirlLayer}. */
    private static final int POWER_TINT = -8355712;
    /**
     * 渲染朝向偏置：{@code +180}°。
     *
     * <p>为什么需要它（本项目 1.1.9 修的头颅反向 bug，推理链可复现）：
     * <ul>
     *   <li>{@link #submit} 照抄原版 {@code WitherSkullRenderer}，在提交模型前先
     *       {@code scale(-1, -1, 1)}。这个缩放等价于绕 Z 轴转 180°，它会让<b>之后</b>在同一个
     *       PoseStack 里做的 Y 轴旋转方向反号（{@code S·R_y(θ) ≠ R_y(-θ)·S}）。</li>
     *   <li>旋转不是本渲染器施加的：{@code SkullModel#setupAnim} 把 {@code modelState.yRot} 写进
     *       {@code ModelPart.yRot}，由 {@code ModelPart} 在<b>已缩放</b>的空间里
     *       {@code Axis.YP.rotationDegrees(yRot)}（先 X 后 Y）。</li>
     *   <li>实体旋转用原版 {@code Projectile#shoot} 约定（{@code yRot = atan2(dz,dx) - 90}，
     *       {@code xRot = -atan2(dy,水平分量)}），{@link com.huziyang520.forbiddenchimera.entity.CreeperSkullProjectile#faceDirection}
     *       每 tick 按真实速度重算，因此角度本身永远正确。</li>
     *   <li>把模型空间的朝向 {@code (0,0,-1)}（苦力怕头颅的脸在 -Z）过一遍
     *       {@code S·R_y(yRot)·R_x(xRot)}：水平分量 = 速度的<b>反向</b>，竖直分量与俯仰一致。
     *       这正是实机看到的"头朝着反方向飞、上下角度却是对的"。</li>
     *   <li>Y 旋转在镜像空间里反号，因此把偏置改成 {@code yRot + 180} 即水平、竖直同时正确，
     *       <b>且俯仰角不需要动</b>（加 180° 是绕 Y 轴，R_x 不受影响）。</li>
     * </ul>
     *
     * <p>放在渲染层而不是改 {@code faceDirection}：实体的 {@code getYRot()} 保持原版
     * {@code shoot} 约定，不会有第二个消费者按错误约定去读它。
     */
    private static final float MODEL_YAW_CORRECTION = 180.0F;

    private final SkullModel model;
    private final boolean powered;

    public ChimeraSkullRenderer(EntityRendererProvider.Context context, boolean powered) {
        super(context);
        this.model = new SkullModel(context.bakeLayer(ModelLayers.CREEPER_HEAD));
        this.powered = powered;
    }

    /** Both skulls glow like the vanilla wither skull, so they read at night. */
    @Override
    protected int getBlockLightLevel(CreeperSkullProjectile entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public void submit(SkullRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        submitNodeCollector.submitModel(this.model, state.modelState, poseStack, CREEPER_SKIN,
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        if (this.powered) {
            float scroll = state.ageInTicks * SCROLL_PER_TICK % 1.0F;
            submitNodeCollector.order(1)
                    .submitModel(this.model, state.modelState, poseStack,
                            RenderTypes.energySwirl(POWER_LOCATION, scroll, scroll),
                            state.lightCoords, OverlayTexture.NO_OVERLAY, POWER_TINT, null,
                            state.outlineColor);
        }
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    public SkullRenderState createRenderState() {
        return new SkullRenderState();
    }

    @Override
    public void extractRenderState(CreeperSkullProjectile entity, SkullRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // No wobble: a missile head is rigid, only its facing changes.
        state.modelState.animationPos = 0.0F;
        state.modelState.yRot = entity.getYRot(partialTicks) + MODEL_YAW_CORRECTION;
        state.modelState.xRot = entity.getXRot(partialTicks);
    }

    /** Carries the vanilla skull model state; the two skulls differ only in the overlay flag. */
    public static class SkullRenderState extends EntityRenderState {
        public final SkullModelBase.State modelState = new SkullModelBase.State();
    }
}
