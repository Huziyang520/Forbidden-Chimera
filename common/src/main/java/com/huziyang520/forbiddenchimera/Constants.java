package com.huziyang520.forbiddenchimera;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for Forbidden Chimera. Keep {@link #MOD_ID} in sync with
 * {@code mod_id} in gradle.properties, with the {@code assets/<mod_id>} folder and with the
 * {@code data/<mod_id>} folder.
 */
public final class Constants {

    public static final String MOD_ID = "forbidden_chimera";
    public static final String MOD_NAME = "Forbidden Chimera";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private Constants() {
    }

    /** Builds a namespaced id inside this mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
