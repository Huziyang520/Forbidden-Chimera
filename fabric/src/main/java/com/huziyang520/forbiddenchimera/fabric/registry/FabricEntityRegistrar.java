package com.huziyang520.forbiddenchimera.fabric.registry;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.platform.services.EntityRegistrar;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Fabric entity registration: straight into the vanilla registry, resolved immediately. */
public class FabricEntityRegistrar implements EntityRegistrar {

    @Override
    public <T extends Entity> Supplier<EntityType<T>> register(String path, Supplier<EntityType.Builder<T>> builder) {
        Identifier id = Constants.id(path);
        EntityType<T> type = builder.get().build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
        return () -> type;
    }
}
