package com.huziyang520.forbiddenchimera.platform.services;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** Loader specific item registration (spawn eggs and the boss summon item live here). */
public interface ItemRegistrar {

    /**
     * Registers an item.
     *
     * <p>26.3 reads the item id from {@link Item.Properties} <b>inside</b> the {@link Item}
     * constructor ({@code Properties#itemIdOrThrow}), so a properties object without an id fails with
     * {@code NullPointerException: Item id not set}. The registrar therefore hands the factory a
     * properties instance that already carries the id.
     *
     * @param path    registry path inside this mod's namespace.
     * @param factory builds the item from the pre-populated properties.
     * @return lazy accessor for the registered item.
     */
    Supplier<Item> register(String path, ItemFactory factory);

    /**
     * Adds an already registered item to a vanilla creative tab.
     *
     * <p>The supplier is resolved lazily: NeoForge only binds items after registration, and the tab
     * contents are built even later, so resolving here would be too early.
     *
     * <p>The tab key is passed in rather than looked up from {@code ModCreativeTabs} on purpose: that
     * class lives in {@code registry}, which already depends on this package, and a default method
     * referencing it would tie the two packages together.
     *
     * @param tab  which vanilla tab to insert into.
     * @param item lazy accessor returned by {@code register(String, ItemFactory)}.
     */
    void addToTab(ResourceKey<CreativeModeTab> tab, Supplier<Item> item);
}
