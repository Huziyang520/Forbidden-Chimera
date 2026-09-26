package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Which chimera a vanilla phantom is playing.
 *
 * <p>The numeric ids are persisted in the phantom's save data, so they are part of the save format:
 * never renumber them, only append.
 */
public enum ChimeraVariant {

    /** Not rolled yet: decided on the phantom's first server tick. */
    UNDECIDED(0),
    /** Rolled but every chimera is disabled: stays a completely vanilla phantom. */
    VANILLA(1),
    /** mob1 幻翼骑士苦力怕: creeper riding on the phantom's back, charges and detonates. */
    RIDER(2),
    /** mob2 苦力怕投手幻翼: creeper hanging below the phantom, hovers and drops it. */
    THROWER(3);

    private final int id;

    ChimeraVariant(int id) {
        this.id = id;
    }

    public int id() {
        return this.id;
    }

    /**
     * Resolves a persisted id.
     *
     * @param id   the stored value.
     * @param fallback value used when the id is unknown (e.g. a save from a newer version).
     */
    public static ChimeraVariant byId(int id, ChimeraVariant fallback) {
        for (ChimeraVariant variant : values()) {
            if (variant.id == id) {
                return variant;
            }
        }
        return fallback;
    }

    /**
     * Weighted roll between the enabled vanilla-combination chimeras.
     *
     * <p>Only the rider and thrower live on vanilla phantoms; the diver (mob3) is a real entity type
     * and never comes out of this roll. Weights are read live so config edits apply to the next spawn.
     */
    public static ChimeraVariant roll(RandomSource random, ForbiddenChimeraConfig config) {
        int riderWeight = config.enablePhantomRiderCreeper ? Math.max(0, config.riderSpawnWeight) : 0;
        int throwerWeight = config.enableCreeperThrowerPhantom ? Math.max(0, config.throwerSpawnWeight) : 0;
        int total = riderWeight + throwerWeight;
        if (total <= 0) {
            return VANILLA;
        }
        return random.nextInt(total) < riderWeight ? RIDER : THROWER;
    }

    /** @return whether this variant carries a creeper at all. */
    public boolean carriesCargo() {
        return this == RIDER || this == THROWER;
    }

    /** @return the matching variant, or {@code null} when the entity plays no chimera role. */
    public static @Nullable ChimeraVariant ofNullable(int id) {
        for (ChimeraVariant variant : values()) {
            if (variant.id == id) {
                return variant;
            }
        }
        return null;
    }
}
