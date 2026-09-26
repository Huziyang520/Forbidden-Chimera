package com.huziyang520.forbiddenchimera.registry;

import com.huziyang520.forbiddenchimera.entity.ChimeraVariant;
import com.huziyang520.forbiddenchimera.item.ChimeraSpawnEggItem;
import com.huziyang520.forbiddenchimera.item.ForbiddenPlankItem;
import com.huziyang520.forbiddenchimera.platform.services.ItemRegistrar;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Items. In 26.3 spawn eggs are data driven: {@code SpawnEggItem} reads the entity type from the
 * {@code minecraft:entity_data} component, so the entity type must be attached through
 * {@code Item.Properties#spawnEgg}. The old {@code (EntityType, colorA, colorB, Properties)}
 * constructor no longer exists.
 *
 * <p>The rider and thrower eggs are {@link ChimeraSpawnEggItem}s instead: those chimeras are vanilla
 * phantom combinations with no entity type, so a data driven egg cannot reference them.
 *
 * <p>Because the item registration happens after entity registration, these suppliers may only be
 * resolved lazily - hence the accessor methods.
 */
public final class ModItems {

    private static Supplier<Item> phantomRiderCreeperSpawnEgg;
    private static Supplier<Item> creeperThrowerPhantomSpawnEgg;
    private static Supplier<Item> creeperPhantomSpawnEgg;
    private static Supplier<Item> lightningCreeperPhantomSpawnEgg;
    private static Supplier<Item> nuclearCreeperPhantomSpawnEgg;
    private static Supplier<Item> endermanPhantomSpawnEgg;
    private static Supplier<Item> forbiddenPlank;

    private ModItems() {
    }

    public static void register(ItemRegistrar registrar) {
        // The properties handed in already carry the item id; 26.3 reads it during Item construction.
        phantomRiderCreeperSpawnEgg = registrar.register("phantom_rider_creeper_spawn_egg",
                properties -> new ChimeraSpawnEggItem(properties, ChimeraVariant.RIDER));

        creeperThrowerPhantomSpawnEgg = registrar.register("creeper_thrower_phantom_spawn_egg",
                properties -> new ChimeraSpawnEggItem(properties, ChimeraVariant.THROWER));

        creeperPhantomSpawnEgg = registrar.register("creeper_phantom_spawn_egg",
                properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.creeperPhantom())));

        lightningCreeperPhantomSpawnEgg = registrar.register("lightning_creeper_phantom_spawn_egg",
                properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.lightningCreeperPhantom())));

        nuclearCreeperPhantomSpawnEgg = registrar.register("nuclear_creeper_phantom_spawn_egg",
                properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.nuclearCreeperPhantom())));

        endermanPhantomSpawnEgg = registrar.register("enderman_phantom_spawn_egg",
                properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.endermanPhantom())));

        // 禁忌板材: the boss summon item. Stacks to 16 so a few attempts can be carried at once; it is
        // epic rarity to match what it costs to craft.
        forbiddenPlank = registrar.register("forbidden_plank",
                properties -> new ForbiddenPlankItem(properties.stacksTo(16).rarity(Rarity.EPIC)));

        // Registered items only show up in the creative menu when a tab lists them; the eggs use the
        // vanilla spawn egg tab. Suppliers are resolved lazily, see ItemRegistrar#addToTab.
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, phantomRiderCreeperSpawnEgg);
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, creeperThrowerPhantomSpawnEgg);
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, creeperPhantomSpawnEgg);
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, lightningCreeperPhantomSpawnEgg);
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, nuclearCreeperPhantomSpawnEgg);
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, endermanPhantomSpawnEgg);
        registrar.addToTab(ModCreativeTabs.INGREDIENTS, forbiddenPlank);
        Supplier<Item> bossSpawnEgg = registrar.register("boss_lightning_creeper_phantom_knight_spawn_egg",
                properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.bossLightningCreeperPhantomKnight())));
        registrar.addToTab(ModCreativeTabs.SPAWN_EGGS, bossSpawnEgg);
    }

    public static Item phantomRiderCreeperSpawnEgg() {
        return phantomRiderCreeperSpawnEgg.get();
    }

    public static Item creeperThrowerPhantomSpawnEgg() {
        return creeperThrowerPhantomSpawnEgg.get();
    }

    public static Item creeperPhantomSpawnEgg() {
        return creeperPhantomSpawnEgg.get();
    }

    public static Item lightningCreeperPhantomSpawnEgg() {
        return lightningCreeperPhantomSpawnEgg.get();
    }

    public static Item nuclearCreeperPhantomSpawnEgg() {
        return nuclearCreeperPhantomSpawnEgg.get();
    }

    public static Item endermanPhantomSpawnEgg() {
        return endermanPhantomSpawnEgg.get();
    }

    /** 禁忌板材: the boss summon item. */
    public static Item forbiddenPlank() {
        return forbiddenPlank.get();
    }
}
