package com.huziyang520.forbiddenchimera.fabric.registry;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.platform.services.ItemFactory;
import com.huziyang520.forbiddenchimera.platform.services.ItemRegistrar;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** Fabric item registration. */
public class FabricItemRegistrar implements ItemRegistrar {

    @Override
    public Supplier<Item> register(String path, ItemFactory factory) {
        // The factory is only invoked once, at registration time; the supplier hands out that instance.
        Identifier id = Constants.id(path);
        // 26.3 needs the id on the properties before the Item constructor runs, otherwise the
        // constructor throws "Item id not set".
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item registered = Registry.register(BuiltInRegistries.ITEM, id,
                factory.create(new Item.Properties().setId(key)));
        return () -> registered;
    }

    @Override
    public void addToTab(ResourceKey<CreativeModeTab> tab, Supplier<Item> item) {
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(item.get()));
    }
}
