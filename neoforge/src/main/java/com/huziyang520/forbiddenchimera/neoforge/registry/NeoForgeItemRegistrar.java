package com.huziyang520.forbiddenchimera.neoforge.registry;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.platform.services.ItemFactory;
import com.huziyang520.forbiddenchimera.platform.services.ItemRegistrar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge item registration. */
public class NeoForgeItemRegistrar implements ItemRegistrar {

    private final DeferredRegister<Item> registry =
            DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);
    /** Tab contents are built after registration, so items are collected per tab and resolved later. */
    private final Map<ResourceKey<CreativeModeTab>, List<Supplier<Item>>> tabItems = new HashMap<>();

    public NeoForgeItemRegistrar(IEventBus modEventBus) {
        this.registry.register(modEventBus);
        modEventBus.addListener(this::onBuildTabContents);
    }

    @Override
    public Supplier<Item> register(String path, ItemFactory factory) {
        // 26.3 needs the id on the properties before the Item constructor runs, otherwise the
        // constructor throws "Item id not set".
        var holder = this.registry.register(path, () -> {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Constants.id(path));
            return factory.create(new Item.Properties().setId(key));
        });
        return holder::get;
    }

    @Override
    public void addToTab(ResourceKey<CreativeModeTab> tab, Supplier<Item> item) {
        this.tabItems.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(item);
    }

    private void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        for (Supplier<Item> item : this.tabItems.getOrDefault(event.getTabKey(), List.of())) {
            event.accept(item.get());
        }
    }
}
