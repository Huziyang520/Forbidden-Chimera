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
     * Weighted roll between the variants a <b>vanilla phantom</b> can become on its own.
     *
     * <p>Used for every phantom whose variant was never assigned by
     * {@link com.huziyang520.forbiddenchimera.world.ChimeraSpawner} - i.e. a vanilla
     * {@code minecraft:phantom} spawn egg, {@code /summon}, or another mod creating one. Before 1.1.9
     * this roll only knew RIDER and THROWER, so <b>every</b> egg/summoned phantom came out as a creeper
     * carrier and a vanilla phantom could not be produced at all.
     *
     * <p>Only the variants that physically fit a vanilla phantom are in here: the diver (mob3),
     * lightning (mob4), nuclear (mob5) and enderman (mob6) chimeras are real registered entity types
     * with their own spawn eggs, and a {@code minecraft:phantom} entity cannot turn into another
     * entity type. Natural spawning picks all seven in {@code ChimeraSpawner#candidates}, which is
     * where the {@code vanillaSpawnWeight} of the full table is honoured.
     *
     * <p>Weights are read live so config edits apply to the next spawn. If every weight is 0 (or the
     * two carriers are disabled) the phantom stays vanilla rather than becoming a chimera.
     */
    public static ChimeraVariant roll(RandomSource random, ForbiddenChimeraConfig config) {
        int vanillaWeight = Math.max(0, config.vanillaSpawnWeight);
        int riderWeight = config.enablePhantomRiderCreeper ? Math.max(0, config.riderSpawnWeight) : 0;
        int throwerWeight = config.enableCreeperThrowerPhantom ? Math.max(0, config.throwerSpawnWeight) : 0;
        int total = vanillaWeight + riderWeight + throwerWeight;
        if (total <= 0) {
            return VANILLA;
        }
        int roll = random.nextInt(total);
        roll -= vanillaWeight;
        if (roll < 0) {
            return VANILLA;
        }
        roll -= riderWeight;
        return roll < 0 ? RIDER : THROWER;
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
