package com.huziyang520.forbiddenchimera.neoforge;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.ForbiddenChimera;
import com.huziyang520.forbiddenchimera.neoforge.registry.NeoForgeEntityRegistrar;
import com.huziyang520.forbiddenchimera.neoforge.registry.NeoForgeItemRegistrar;
import com.huziyang520.forbiddenchimera.registry.ModAttributes;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(Constants.MOD_ID)
public class ForbiddenChimeraNeoForge {

    public ForbiddenChimeraNeoForge(IEventBus modEventBus) {
        ForbiddenChimera.init(new NeoForgeEntityRegistrar(modEventBus), new NeoForgeItemRegistrar(modEventBus),
                FMLPaths.CONFIGDIR.get());

        // These entity types are absent from vanilla DefaultAttributes, so they must be registered
        // through the event or spawning one crashes the game.
        modEventBus.addListener(this::onEntityAttributeCreation);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        // mob1/mob2 are vanilla phantoms and already have vanilla attributes.
        event.put(ModEntities.creeperPhantom(), ModAttributes.creeperPhantom());
        event.put(ModEntities.lightningCreeperPhantom(), ModAttributes.lightningCreeperPhantom());
        event.put(ModEntities.nuclearCreeperPhantom(), ModAttributes.nuclearCreeperPhantom());
        event.put(ModEntities.endermanPhantom(), ModAttributes.endermanPhantom());
        event.put(ModEntities.bossLightningCreeperPhantomKnight(),
                ModAttributes.bossLightningCreeperPhantomKnight());
    }
}
