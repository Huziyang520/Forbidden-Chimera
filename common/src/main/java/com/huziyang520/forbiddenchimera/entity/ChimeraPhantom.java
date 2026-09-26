package com.huziyang520.forbiddenchimera.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

/**
 * Everything a vanilla phantom needs to play a chimera role, added by {@code PhantomMixin}.
 *
 * <p>The rider and thrower are <b>not</b> registered entity types: they are genuine vanilla phantoms
 * carrying a genuine vanilla creeper, and this interface is the whole custom surface. Steering is
 * shared with the real chimera (mob3) through {@code ChimeraSteering}.
 */
public interface ChimeraPhantom {

    /** @return which chimera this phantom plays; never {@code null} after the first server tick. */
    ChimeraVariant forbiddenChimera$variant();

    /** Assigns the variant before the phantom joins the world (spawner and spawn eggs use this). */
    void forbiddenChimera$setVariant(ChimeraVariant variant);

    /** @return the live carried creeper, or {@code null} when its claws are empty. */
    @Nullable Creeper forbiddenChimera$cargo();

    /** @return whether the throw cooldown has run out. */
    boolean forbiddenChimera$canThrow();

    /** Thrower only: releases the cargo at the target and immediately grabs a replacement. */
    void forbiddenChimera$dropCargo(LivingEntity target);

    /** Rider only: detonates the cargo and takes the phantom down with it (exactly once). */
    void forbiddenChimera$detonateCargo();
}
