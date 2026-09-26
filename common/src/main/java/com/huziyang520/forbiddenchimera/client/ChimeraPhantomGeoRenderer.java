package com.huziyang520.forbiddenchimera.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renderer for every model based chimera: the Creeper Phantom (mob3), the Lightning Creeper Phantom
 * (mob4), the Nuclear Creeper Phantom (mob5), the Enderman Phantom (mob6) and the boss.
 *
 * <p>They share geometry and animation plumbing, so this renderer takes the geometry, the texture, the
 * animation set, which bones carry the charged overlay and whether to add the spear layer as arguments
 * instead of being copied five times.
 */
public class ChimeraPhantomGeoRenderer
        extends GeoEntityRenderer<CreeperPhantomEntity, LivingEntityRenderState> {

    /**
     * Bones that make up the <b>creeper</b> part of the model - the charged overlay is applied exactly
     * to these, so the phantom wings and tail stay plain.
     *
     * <p>Verified against {@code creeper_phantom.geo.json} (parsed, not eyeballed):
     * <ul>
     *   <li>{@code body} - the torso: 6x5x16 at UV (0,64) plus 5x3.5x2 at UV (0,64). This is the part
     *       the model reads as the chimera's body, so it is charged;</li>
     *   <li>{@code head} - 8x8x8 with the vanilla creeper head UV layout
     *       (8,8)/(0,8)/(24,8)/(16,8)/(8,0)/(16,0), which is why vanilla {@code creeper_armor.png}
     *       maps onto it correctly;</li>
     *   <li>wings sit at UV (96,12)/(102,12)/(64,64) and the tail at (96,12)/(64,64) - never charged.</li>
     * </ul>
     */
    private static final String[] CREEPER_BONES = {"body", "head"};

    /**
     * The boss's equivalent: it is the same model with every bone renamed with a {@code boss_} prefix.
     * Parsed from {@code boss_lightning_creeper_phantom_knight.geo.json}: {@code boss_body} is the same
     * 6x5x16 phantom torso and {@code boss_head} is the same 8x8x8 creeper head, so the vanilla
     * {@code creeper_armor.png} swirl maps onto both exactly as it does for mob4. The warden and dragon
     * heads ({@code boss_warden_head} / {@code boss_dragon_head_*}) are deliberately <b>not</b> charged.
     */
    public static final String[] BOSS_CHARGED_BONES = {"boss_body", "boss_head"};

    /** Nothing charged: for variants that keep the plain texture. */
    private static final String[] NO_BONES = new String[0];

    /** Vanilla phantom sized blob: the shadow every chimera had before the boss existed. */
    private static final float DEFAULT_SHADOW_RADIUS = 0.75F;

    /** Creeper family: shares {@code creeper_phantom} geometry. */
    public ChimeraPhantomGeoRenderer(EntityRendererProvider.Context context, Identifier texture,
                                     Identifier animation, boolean charged, boolean spearCarrier) {
        this(context, ChimeraPhantomGeoModel.CREEPER_GEOMETRY, texture, animation, charged, spearCarrier);
    }

    /** Creeper family with an explicit geometry (the enderman swaps the body and head). */
    public ChimeraPhantomGeoRenderer(EntityRendererProvider.Context context, Identifier geometry,
                                     Identifier texture, Identifier animation, boolean charged,
                                     boolean spearCarrier) {
        this(context, geometry, texture, animation, charged ? CREEPER_BONES : NO_BONES,
                DEFAULT_SHADOW_RADIUS, spearCarrier);
    }

    /**
     * The canonical constructor.
     *
     * @param chargedBones bones that get the vanilla charged creeper swirl added on top of their normal
     *                     texture; empty means "no overlay".
     * @param shadowRadius vanilla shadow size, which has to grow with the model.
     * @param spearCarrier registers the spear layer, so this archetype can show the spear variant.
     *                     Only the diving creeper chimeras (mob3 / mob4) can carry a spear.
     */
    public ChimeraPhantomGeoRenderer(EntityRendererProvider.Context context, Identifier geometry,
                                     Identifier texture, Identifier animation, String[] chargedBones,
                                     float shadowRadius, boolean spearCarrier) {
        super(context, new ChimeraPhantomGeoModel(geometry, texture, animation));
        this.shadowRadius = shadowRadius;
        for (String bone : chargedBones) {
            // The layer is typed against GeckoLib's GeoRenderState bound, hence the raw cast.
            this.withRenderLayer(new LightningOverlayGeoLayer(this, bone));
        }
        if (spearCarrier) {
            this.withRenderLayer(new SpearGeoLayer(this, context));
        }
    }
}
