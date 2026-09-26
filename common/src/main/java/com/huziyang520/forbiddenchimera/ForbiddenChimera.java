package com.huziyang520.forbiddenchimera;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.platform.Services;
import com.huziyang520.forbiddenchimera.platform.services.EntityRegistrar;
import com.huziyang520.forbiddenchimera.platform.services.ItemRegistrar;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import com.huziyang520.forbiddenchimera.registry.ModItems;
import java.nio.file.Path;

/**
 * Loader independent bootstrap. Both the Fabric and the NeoForge entry point call this exactly once,
 * early during mod construction, after their own loaders' registration handles are ready.
 */
public final class ForbiddenChimera {

    private static boolean initialised;

    private ForbiddenChimera() {
    }

    /**
     * @param entityRegistrar loader specific entity type registration.
     * @param itemRegistrar   loader specific item registration.
     * @param configDir       the loader's {@code config} directory.
     */
    public static void init(EntityRegistrar entityRegistrar, ItemRegistrar itemRegistrar, Path configDir) {
        if (initialised) {
            Constants.LOG.warn("Forbidden Chimera was already initialised, ignoring the second call");
            return;
        }
        initialised = true;

        ForbiddenChimeraConfig.load(configDir);
        ModEntities.register(entityRegistrar);
        ModItems.register(itemRegistrar);

        Constants.LOG.info("Forbidden Chimera loaded on {} ({})",
                Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }
}
