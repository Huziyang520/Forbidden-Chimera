package com.huziyang520.forbiddenchimera.neoforge.client;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.client.ChimeraPhantomGeoModel;
import com.huziyang520.forbiddenchimera.client.ChimeraPhantomGeoRenderer;
import com.huziyang520.forbiddenchimera.client.ChimeraSkullRenderer;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only renderer registration. {@code value = Dist.CLIENT} keeps this class out of the dedicated
 * server, where the client renderer types do not exist.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class ForbiddenChimeraNeoForgeClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // mob1/mob2 are vanilla phantoms: the vanilla PhantomRenderer draws them, with the vanilla mod
        // geometry, and nothing needs to be registered or patched for them.
        // The two model based chimeras share geometry, animation and texture; the charged variant only
        // adds the vanilla charged creeper overlay on the creeper bones.
        event.registerEntityRenderer(ModEntities.creeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.PLAIN_TEXTURE,
                        ChimeraPhantomGeoModel.PLAIN_ANIMATION, false, true));
        event.registerEntityRenderer(ModEntities.lightningCreeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.PLAIN_TEXTURE,
                        ChimeraPhantomGeoModel.PLAIN_ANIMATION, true, true));
        event.registerEntityRenderer(ModEntities.nuclearCreeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.NUCLEAR_TEXTURE,
                        ChimeraPhantomGeoModel.NUCLEAR_ANIMATION, false, false));
        // The enderman variant has its own geometry (enderman body and head) but the shared animation
        // set: the wing and tail bone names are identical, so no new animations are needed.
        event.registerEntityRenderer(ModEntities.endermanPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.ENDERMAN_GEOMETRY,
                        ChimeraPhantomGeoModel.ENDERMAN_TEXTURE, ChimeraPhantomGeoModel.PLAIN_ANIMATION,
                        false, false));
        // The boss has its own geometry, texture and seven clip animation set; it keeps the vanilla
        // charged swirl on its creeper parts (the boss_ bones) and gets a bigger shadow to match.
        event.registerEntityRenderer(ModEntities.bossLightningCreeperPhantomKnight(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.BOSS_GEOMETRY,
                        ChimeraPhantomGeoModel.BOSS_TEXTURE, ChimeraPhantomGeoModel.BOSS_ANIMATION,
                        ChimeraPhantomGeoRenderer.BOSS_CHARGED_BONES, 1.5F, false));
        // Both explosive skulls use the vanilla skull model (not the item renderer), so the head points
        // along its flight direction; the lightning one adds the vanilla energy swirl on top.
        event.registerEntityRenderer(ModEntities.creeperSkull(),
                context -> new ChimeraSkullRenderer(context, false));
        event.registerEntityRenderer(ModEntities.lightningCreeperSkull(),
                context -> new ChimeraSkullRenderer(context, true));
    }
}
