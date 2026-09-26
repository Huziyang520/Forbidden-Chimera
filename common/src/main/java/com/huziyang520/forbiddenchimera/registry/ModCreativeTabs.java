package com.huziyang520.forbiddenchimera.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Creative tab keys this mod inserts into.
 *
 * <p>{@code CreativeModeTabs.SPAWN_EGGS} is private in 26.3, so the vanilla key is rebuilt from its
 * id instead. Both loaders compare against the same value: Fabric through
 * {@code CreativeModeTabEvents}, NeoForge through {@code BuildCreativeModeTabContentsEvent}.
 */
public final class ModCreativeTabs {

    /** The vanilla spawn egg tab, {@code minecraft:spawn_eggs}. */
    public static final ResourceKey<CreativeModeTab> SPAWN_EGGS =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));

    /** The vanilla ingredients tab, {@code minecraft:ingredients} - where the summon item belongs. */
    public static final ResourceKey<CreativeModeTab> INGREDIENTS =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));

    private ModCreativeTabs() {
    }
}
