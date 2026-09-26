package com.huziyang520.forbiddenchimera.neoforge.registry;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.platform.services.EntityRegistrar;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entity registration.
 *
 * <p>Unlike Fabric, entries must go through a {@link DeferredRegister} so they land in NeoForge's
 * registry snapshots. The returned supplier therefore delegates to the deferred holder and only
 * resolves once registration has actually happened.
 */
public class NeoForgeEntityRegistrar implements EntityRegistrar {

    private final DeferredRegister<EntityType<?>> registry =
            DeferredRegister.create(Registries.ENTITY_TYPE, Constants.MOD_ID);

    public NeoForgeEntityRegistrar(IEventBus modEventBus) {
        this.registry.register(modEventBus);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> Supplier<EntityType<T>> register(String path, Supplier<EntityType.Builder<T>> builder) {
        var holder = this.registry.register(path,
                () -> builder.get().build(ResourceKey.create(Registries.ENTITY_TYPE, Constants.id(path))));
        return () -> (EntityType<T>) holder.get();
    }
}
