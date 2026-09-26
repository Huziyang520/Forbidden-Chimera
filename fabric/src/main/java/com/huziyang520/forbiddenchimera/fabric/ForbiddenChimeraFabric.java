package com.huziyang520.forbiddenchimera.fabric;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.ForbiddenChimera;
import com.huziyang520.forbiddenchimera.fabric.registry.FabricEntityRegistrar;
import com.huziyang520.forbiddenchimera.fabric.registry.FabricItemRegistrar;
import com.huziyang520.forbiddenchimera.registry.ModAttributes;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;

public class ForbiddenChimeraFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ForbiddenChimera.init(new FabricEntityRegistrar(), new FabricItemRegistrar(),
                FabricLoader.getInstance().getConfigDir());

        // These entity types are absent from vanilla DefaultAttributes, so they must be registered
        // explicitly or spawning one crashes the game. mob1/mob2 are vanilla phantoms and already
        // have vanilla attributes.
        FabricDefaultAttributeRegistry.register(ModEntities.creeperPhantom(), ModAttributes.creeperPhantom());
        FabricDefaultAttributeRegistry.register(ModEntities.lightningCreeperPhantom(),
                ModAttributes.lightningCreeperPhantom());
        FabricDefaultAttributeRegistry.register(ModEntities.nuclearCreeperPhantom(),
                ModAttributes.nuclearCreeperPhantom());
        FabricDefaultAttributeRegistry.register(ModEntities.endermanPhantom(),
                ModAttributes.endermanPhantom());
        FabricDefaultAttributeRegistry.register(ModEntities.bossLightningCreeperPhantomKnight(),
                ModAttributes.bossLightningCreeperPhantomKnight());

        Constants.LOG.info("Forbidden Chimera: entities registered for Fabric");
    }
}
