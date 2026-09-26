package com.huziyang520.forbiddenchimera.platform.services;

import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Loader specific entity type registration.
 *
 * <p>Fabric registers straight into the vanilla {@code BuiltInRegistries.ENTITY_TYPE}; NeoForge has to
 * go through a {@code DeferredRegister} so the entries end up in its registry snapshots. Because of that
 * difference, common code never stores an {@link EntityType} directly - it stores a
 * {@link Supplier} that only gets resolved after registration has completed.
 */
public interface EntityRegistrar {

    <T extends Entity> Supplier<EntityType<T>> register(String path, Supplier<EntityType.Builder<T>> builder);
}
