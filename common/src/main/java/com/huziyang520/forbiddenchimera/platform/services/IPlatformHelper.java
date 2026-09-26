package com.huziyang520.forbiddenchimera.platform.services;

/**
 * Generic platform information, loaded through {@link java.util.ServiceLoader} so that common code can
 * ask loader questions without importing loader classes.
 */
public interface IPlatformHelper {

    /** @return the name of the current platform. */
    String getPlatformName();

    /**
     * @param modId the mod to check.
     * @return true when a mod with this id is loaded.
     */
    boolean isModLoaded(String modId);

    /** @return true when running inside a development environment. */
    boolean isDevelopmentEnvironment();

    /** @return "development" or "production". */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
