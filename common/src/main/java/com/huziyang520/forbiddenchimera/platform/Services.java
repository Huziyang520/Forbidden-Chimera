package com.huziyang520.forbiddenchimera.platform;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/**
 * Service locator used to reach the loader specific implementation of {@link IPlatformHelper}.
 * The implementation is declared in {@code META-INF/services} of each loader module.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
