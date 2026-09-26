package com.huziyang520.forbiddenchimera.fabric.client;

import com.huziyang520.forbiddenchimera.client.ChimeraPhantomGeoModel;
import com.huziyang520.forbiddenchimera.client.ChimeraPhantomGeoRenderer;
import com.huziyang520.forbiddenchimera.client.ChimeraSkullRenderer;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ForbiddenChimeraFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // mob1/mob2 are vanilla phantoms: the vanilla PhantomRenderer draws them, with the vanilla mod
        // geometry, and nothing needs to be registered or patched for them.
        // The two model based chimeras share geometry, animation and texture; the charged variant only
        // adds the vanilla charged creeper overlay on the creeper bones.
        EntityRendererRegistry.register(ModEntities.creeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.PLAIN_TEXTURE,
                        ChimeraPhantomGeoModel.PLAIN_ANIMATION, false, true));
        EntityRendererRegistry.register(ModEntities.lightningCreeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.PLAIN_TEXTURE,
                        ChimeraPhantomGeoModel.PLAIN_ANIMATION, true, true));
        EntityRendererRegistry.register(ModEntities.nuclearCreeperPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.NUCLEAR_TEXTURE,
                        ChimeraPhantomGeoModel.NUCLEAR_ANIMATION, false, false));
        // The enderman variant has its own geometry (enderman body and head) but the shared animation
        // set: the wing and tail bone names are identical, so no new animations are needed.
        EntityRendererRegistry.register(ModEntities.endermanPhantom(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.ENDERMAN_GEOMETRY,
                        ChimeraPhantomGeoModel.ENDERMAN_TEXTURE, ChimeraPhantomGeoModel.PLAIN_ANIMATION,
                        false, false));
        // The boss has its own geometry, texture and seven clip animation set; it keeps the vanilla
        // charged swirl on its creeper parts (the boss_ bones) and gets a bigger shadow to match.
        EntityRendererRegistry.register(ModEntities.bossLightningCreeperPhantomKnight(),
                context -> new ChimeraPhantomGeoRenderer(context, ChimeraPhantomGeoModel.BOSS_GEOMETRY,
                        ChimeraPhantomGeoModel.BOSS_TEXTURE, ChimeraPhantomGeoModel.BOSS_ANIMATION,
                        ChimeraPhantomGeoRenderer.BOSS_CHARGED_BONES, 1.5F, false));
        // Both explosive skulls use the vanilla skull model (not the item renderer), so the head points
        // along its flight direction; the lightning one adds the vanilla energy swirl on top.
        EntityRendererRegistry.register(ModEntities.creeperSkull(),
                context -> new ChimeraSkullRenderer(context, false));
        EntityRendererRegistry.register(ModEntities.lightningCreeperSkull(),
                context -> new ChimeraSkullRenderer(context, true));
    }
}
