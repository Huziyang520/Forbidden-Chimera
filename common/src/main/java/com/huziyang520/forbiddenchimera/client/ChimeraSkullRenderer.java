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
        state.modelState.yRot = entity.getYRot(partialTicks);
        state.modelState.xRot = entity.getXRot(partialTicks);
    }

    /** Carries the vanilla skull model state; the two skulls differ only in the overlay flag. */
    public static class SkullRenderState extends EntityRenderState {
        public final SkullModelBase.State modelState = new SkullModelBase.State();
    }
}
