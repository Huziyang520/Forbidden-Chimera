package com.huziyang520.forbiddenchimera.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the spear of the spear variant (mob3 / mob4) hanging under the chimera's wing.
 *
 * <p>It renders the <b>real</b> carried {@link ItemStack} through the vanilla item model resolver, not
 * a baked copy: the seven vanilla spears all work without a single new model or texture, and a resource
 * pack that restyles a spear - or an item that replaces it - shows up here unchanged. That is the whole
 * reason the project does not ship seven fixed spear models.
 *
 * <p>Two spears, one per wing - the spear variant is symmetric. Each follows its <b>own</b> wing bone
 * (see {@link #LEFT_SPEAR_BONE} / {@link #RIGHT_SPEAR_BONE}), so both inherit their side's wing
 * animation for free and stay flat under the wing surface. The exact mount point, orientation and scale
 * are <b>per-bone geometry</b> and can only be confirmed with an in-game F3+B pass; per this project's
 * rules they are kept as named constants ({@link #MOUNT_SPAN_OUT} and friends) here rather than pushed
 * into the config file.
 *
 * <p>Why the raw type: GeckoLib bounds a layer's state parameter by its own {@code GeoRenderState},
 * while this renderer is typed with the vanilla {@code LivingEntityRenderState} - the same compile time
 * mismatch {@link LightningOverlayGeoLayer} bridges. At runtime the vanilla state does implement
 * {@code GeoRenderState}, so the raw bridge is safe. The overrides therefore use the <b>erased</b>
 * signatures of {@code BlockAndItemGeoLayer}; that is a raw-supertype requirement, not a mistake.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpearGeoLayer extends BlockAndItemGeoLayer {

    /**
     * The two bones the spears hang from. Verified by parsing {@code creeper_phantom.geo.json} rather
     * than by eye: the wing bones are {@code left_wing_base} / {@code left_wing_tip} /
     * {@code right_wing_base} / {@code right_wing_tip} and the tail is {@code tail_base} /
     * {@code tail_tip}. Both wing <b>roots</b> are the natural hangers - a spear mounted on a wing
     * <b>tip</b> would swing with the outermost segment and leave the feather line.
     */
    public static final String LEFT_SPEAR_BONE = "left_wing_base";
    public static final String RIGHT_SPEAR_BONE = "right_wing_base";

    /**
     * How far out along the wing span each spear sits, in model units (16 units = 1 block).
     *
     * <p>Direction comes from the geometry: the left wing's cubes run {@code x = +3 .. +15} and the
     * right wing's {@code x = -3 .. -15}, so "outward" is {@code +x} on the left and {@code -x} on the
     * right. 6 units puts the spear around the middle of the inner wing panel, which is wide enough to
     * hide it. <b>The exact value still needs the in-game F3+B pass</b> - it is a per-bone geometry
     * constant, not a config value (project rule).
     */
    private static final double MOUNT_SPAN_OUT = 6.0D;

    /**
     * How far below the wing each spear sits, in model units. The wing plates are 1 unit thick, so one
     * more unit puts the spear just under the wing surface instead of inside it.
     */
    private static final double MOUNT_DROP = 1.0D;

    /** Flat-under-the-wing pose: the item model is authored to point out of a hand, so it is laid down. */
    private static final float MOUNT_PITCH_DEGREES = -90.0F;

    public SpearGeoLayer(GeoRenderer renderer, EntityRendererProvider.Context context) {
        super(context, renderer);
    }

    /**
     * Builds the render data for this frame: nothing at all unless this individual actually carries a
     * spear, which keeps the ordinary chimeras completely free of the layer's cost.
     */
    @Override
    protected List getRelevantBones(GeoAnimatable animatable, Object relatedObject, GeoRenderState renderState,
                                   float partialTick) {
        if (!(animatable instanceof CreeperPhantomEntity entity)) {
            return List.of();
        }
        ItemStack spear = entity.spearStack();
        if (spear.isEmpty()) {
            return List.of();
        }

        ItemStackRenderState itemState = new ItemStackRenderState();
        // Third person context, matching how a weapon carried by a mob should be lit and oriented.
        this.itemModelResolver.updateForLiving(itemState, spear, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        // One spear per wing, and each is mounted on its own wing bone, so the two inherit their own
        // side's animation instead of one of them being a mirrored copy of the other.
        return List.of(
                RenderData.item(LEFT_SPEAR_BONE, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, itemState),
                RenderData.item(RIGHT_SPEAR_BONE, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, itemState));
    }

    @Override
    protected void submitItemStackRender(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         com.geckolib.cache.model.GeoBone bone,
                                         ItemStackRenderState itemState, ItemDisplayContext displayContext,
                                         GeoRenderState renderState,
                                         net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight) {
        // Outward along the bone's own local x axis: +x on the left wing, -x on the right one.
        double outward = (LEFT_SPEAR_BONE.equals(bone.name()) ? 1.0D : -1.0D) * MOUNT_SPAN_OUT / 16.0D;
        poseStack.pushPose();
        try {
            poseStack.translate(outward, -MOUNT_DROP / 16.0D, 0.0D);
            // 平放：绕 X 轴压 -90°（26.3 的 PoseStack 只有 rotate / rotateDegrees，没有 mulPose(Quaternionf)）。
            poseStack.rotateDegrees(com.mojang.math.Axis.XP, MOUNT_PITCH_DEGREES);
            super.submitItemStackRender(poseStack, bone, itemState, displayContext, renderState, collector, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    /** Identical to the stock implementation, see the class javadoc for the erased signatures. */
    @Override
    public void addRenderData(GeoAnimatable animatable, Object relatedObject, GeoRenderState renderState,
                              float partialTick) {
        List data = this.getRelevantBones(animatable, relatedObject, renderState, partialTick);
        if (!data.isEmpty()) {
            renderState.addGeckolibData(BlockAndItemGeoLayer.CONTENTS, data);
            renderState.addGeckolibData(DataTickets.IS_LEFT_HANDED, Boolean.FALSE);
        }
    }
}
