package com.huziyang520.forbiddenchimera.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Vanilla charged creeper effect: the creeper bones keep their normal texture and get the energy swirl
 * <b>added on top</b>. Exact same recipe as vanilla {@code CreeperPowerLayer}: texture
 * {@code textures/entity/creeper/creeper_armor.png} through
 * {@code RenderTypes.energySwirl(texture, u, v)} with both offsets scrolling at {@code age * 0.01}.
 *
 * <p>Two deliberate deviations from the stock {@code CustomBoneTextureGeoLayer}, both required to match
 * vanilla behaviour rather than "custom texture" behaviour:
 * <ol>
 *   <li>{@link #preRender} is a no-op. The stock implementation calls {@code BoneSnapshot.skipRender(true)}
 *       and {@code skipChildrenRender(true)}, i.e. it <b>hides the bone in the normal pass</b> so it can
 *       take over its texture. That made the creeper torso and head render as transparent ghosts: only
 *       the mostly transparent armour swirl was drawn, the green base was gone. Vanilla draws both, and
 *       GeckoLib runs layers after the base pass, so the swirl naturally shades over the base.</li>
 * </ol>
 *
 * <p>The raw types are deliberate: the layer's state parameter is bounded by GeckoLib's
 * {@code GeoRenderState}, while this renderer is typed with the vanilla {@code LivingEntityRenderState}
 * (which is what {@code GeoEntityRenderer} itself accepts). At runtime the vanilla state implements
 * {@code GeoRenderState}, so the raw bridge is safe; the animated age is read through the interface.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class LightningOverlayGeoLayer extends CustomBoneTextureGeoLayer {

    /** Identical to vanilla {@code CreeperPowerLayer#POWER_LOCATION}. */
    private static final Identifier POWER_LOCATION =
            Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");

    /** Vanilla scroll speed: {@code t * 0.01} for both uv axes. */
    private static final float SCROLL_PER_TICK = 0.01F;

    public LightningOverlayGeoLayer(GeoRenderer renderer, String boneName) {
        super(renderer, boneName, POWER_LOCATION);
    }

    /** Deliberately empty: the bone must stay visible with its normal texture (see class javadoc). */
    @Override
    public void preRender(RenderPassInfo renderInfo, SubmitNodeCollector collector) {
    }

    @Override
    protected RenderType getRenderType(GeoRenderState state, Identifier texture) {
        float scroll = (float) state.getAnimatableAge() * SCROLL_PER_TICK % 1.0F;
        return RenderTypes.energySwirl(texture, scroll, scroll);
    }
}
